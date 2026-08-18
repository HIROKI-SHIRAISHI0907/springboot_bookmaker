// dev/common/lock/PgAdvisoryLock.java
package dev.common.lock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Callable;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * PostgreSQLのadvisory lockを使った、プロセス間・JVM間の排他制御ユーティリティ。
 * 同一DBに接続する複数のバッチ/APIプロセスの間で、S3上の特定prefixへの
 * read-check-write処理を直列化するために使用する。
 *
 * pg_advisory_lock(ブロッキング)ではなく pg_try_advisory_lock をポーリングし、
 * maxWaitMillis以内に取得できなければロック取得失敗として例外を投げる。
 * (何らかの理由でロック保持側が固まった場合に、待ち側が無期限にブロックし続けるのを避けるため)
 */
@Component
@RequiredArgsConstructor
public class PgAdvisoryLock {

	private static final long DEFAULT_MAX_WAIT_MILLIS = 30_000L;
	private static final long POLL_INTERVAL_MILLIS = 200L;

	private DataSource dataSource;

	public PgAdvisoryLock(@Qualifier("bmDataSource") DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public <T> T runExclusive(long lockKey, Callable<T> action) throws Exception {
		return runExclusive(lockKey, DEFAULT_MAX_WAIT_MILLIS, action);
	}

	public <T> T runExclusive(long lockKey, long maxWaitMillis, Callable<T> action) throws Exception {
		try (Connection conn = dataSource.getConnection()) {
			acquire(conn, lockKey, maxWaitMillis);
			try {
				return action.call();
			} finally {
				release(conn, lockKey);
			}
		}
	}

	private void acquire(Connection conn, long lockKey, long maxWaitMillis) throws SQLException, InterruptedException {
		final long deadline = System.currentTimeMillis() + maxWaitMillis;
		while (true) {
			try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
				ps.setLong(1, lockKey);
				try (ResultSet rs = ps.executeQuery()) {
					rs.next();
					if (rs.getBoolean(1)) {
						return; // ロック取得成功
					}
				}
			}
			if (System.currentTimeMillis() >= deadline) {
				throw new IllegalStateException(
						"b008出力の排他ロック取得がタイムアウトしました(lockKey=" + lockKey + ")");
			}
			Thread.sleep(POLL_INTERVAL_MILLIS);
		}
	}

	private void release(Connection conn, long lockKey) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
			ps.setLong(1, lockKey);
			ps.execute();
		}
	}
}