package dev.application.main.service;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * DB の接続断・一時的なエラーのときだけ処理をやり直す共通部品。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * 処理を実行し、接続断系の例外（{@link #isRetryable}）なら待ってやり直す（最大 {@link #MAX_ATTEMPTS} 回）。
 * それ以外の例外・回数切れはそのまま投げる。
 * MainStat と CoreStat に同じコード（runWithRetry / isRetryableDbException / sleepQuietly）があったのをまとめた。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>やり直してよい処理だけに使うこと</b>（同じ結果になる処理。各 Stat の保存は UPSERT なので可）。</li>
 *   <li><b>入れ子にしないこと</b>: 外側と内側の両方で使うと、最大 3 × 3 = 9 回実行される。</li>
 *   <li>メッセージの文字列での判定は JDBC ドライバの文言に依存する（ドライバを変えたら確認すること）。</li>
 * </ul>
 */
@Component
public class DbRetryExecutor {

	private static final String PROJECT_NAME = DbRetryExecutor.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = DbRetryExecutor.class.getName();

	/** 最大実行回数（初回を含む） */
	static final int MAX_ATTEMPTS = 3;

	/** やり直すまでの待ち時間（ミリ秒） */
	static final long WAIT_MILLIS = 3000L;

	/** 接続断と判断するメッセージ（小文字で比較） */
	private static final String[] RETRYABLE_MESSAGES = {
			"connection is closed",
			"connection has been closed",
			"broken pipe",
			"connection reset",
			"communications link failure",
			"could not open jdbc connection",
			"failed to obtain jdbc connection",
			"the connection attempt failed",
			"socket closed",
			"connection refused",
			"i/o error occurred while sending to the backend"
	};

	@Autowired
	private ManageLoggerComponent loggerComponent;

	/**
	 * 値を返す処理を実行する（接続断系ならやり直す）。
	 *
	 * @param processName ログに出す処理名
	 * @param supplier 処理
	 * @return 処理の戻り値
	 * @throws Exception やり直しても失敗した場合・やり直さない例外の場合
	 */
	public <T> T call(String processName, CheckedSupplier<T> supplier) throws Exception {
		final String METHOD_NAME = "call";
		int attempt = 0;
		while (true) {
			attempt++;
			try {
				return supplier.get();
			} catch (Exception e) {
				boolean retryable = isRetryable(e);
				if (!retryable || attempt >= MAX_ATTEMPTS) {
					this.loggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00099I_LOG, null,
							"retry give up. process=" + processName + ", attempt=" + attempt
									+ ", retryable=" + retryable + ", message=" + safe(e.getMessage()));
					throw e;
				}
				this.loggerComponent.debugWarnLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099I_LOG,
						"retry execute. process=" + processName + ", attempt=" + attempt + "/" + MAX_ATTEMPTS
								+ ", waitMillis=" + WAIT_MILLIS + ", message=" + safe(e.getMessage()));
				sleep(WAIT_MILLIS);
			}
		}
	}

	/**
	 * 値を返さない処理を実行する（接続断系ならやり直す）。
	 *
	 * @param processName ログに出す処理名
	 * @param runnable 処理
	 * @throws Exception やり直しても失敗した場合・やり直さない例外の場合
	 */
	public void run(String processName, CheckedRunnable runnable) throws Exception {
		call(processName, () -> {
			runnable.run();
			return null;
		});
	}

	/**
	 * 接続断・一時的な DB エラーか（原因の連鎖をたどって判定）。
	 */
	static boolean isRetryable(Throwable t) {
		Throwable current = t;
		while (current != null) {
			if (current instanceof CannotGetJdbcConnectionException
					|| current instanceof CannotCreateTransactionException
					|| current instanceof TransientDataAccessException
					|| current instanceof RecoverableDataAccessException) {
				return true;
			}
			if (current instanceof SQLException) {
				String state = ((SQLException) current).getSQLState();
				if (state != null && state.startsWith("08")) {
					return true;
				}
			}
			String className = current.getClass().getName();
			if (className.contains("SQLTransientConnectionException")
					|| className.contains("SQLRecoverableException")) {
				return true;
			}
			String message = safe(current.getMessage()).toLowerCase();
			for (String m : RETRYABLE_MESSAGES) {
				if (message.contains(m)) {
					return true;
				}
			}
			current = current.getCause();
		}
		return false;
	}

	private static void sleep(long millis) throws InterruptedException {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		}
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	/** 例外を投げてよい Supplier */
	@FunctionalInterface
	public interface CheckedSupplier<T> {
		T get() throws Exception;
	}

	/** 例外を投げてよい Runnable */
	@FunctionalInterface
	public interface CheckedRunnable {
		void run() throws Exception;
	}
}