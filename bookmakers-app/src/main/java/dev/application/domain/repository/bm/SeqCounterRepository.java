package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Options.FlushCachePolicy;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 採番カウンタ（seq_counter）Mapper。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * 「テーブル名 × シーズン」ごとの最終番号を1つ進めて返す。
 * PostgreSQL の INSERT ... ON CONFLICT DO UPDATE ... RETURNING を使い、
 * 「行がなければ 1 で作成、あれば +1」を1文で原子的に行う。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>MyBatis のローカルキャッシュ</b>: 同じトランザクション内で同じ引数の @Select を2回呼ぶと、
 *       2回目は DB に行かずキャッシュ値（同じ番号）が返る。
 *       {@code flushCache = TRUE} / {@code useCache = false} を必ず付けること（本メソッドは対応済み）。</li>
 *   <li><b>行ロックの保持時間</b>: 番号を進めた行は、呼び出し元のトランザクションが終わるまでロックされる。
 *       同じテーブル×シーズンを採番する他の処理は、その間待たされる（番号の重複・欠番は起きない）。</li>
 *   <li><b>PostgreSQL 専用構文</b>: 他の DB に移行する場合は書き換えが必要。</li>
 * </ul>
 *
 * <p>DDL:</p>
 * <pre>
 * CREATE TABLE IF NOT EXISTS seq_counter (
 *   table_name    VARCHAR(100)                NOT NULL,
 *   season        VARCHAR(20)                 NOT NULL,
 *   last_no       BIGINT                      NOT NULL,
 *   register_id   VARCHAR(100)                NOT NULL,
 *   register_time TIMESTAMP(0) WITH TIME ZONE NOT NULL,
 *   update_id     VARCHAR(100)                NOT NULL,
 *   update_time   TIMESTAMP(0) WITH TIME ZONE NOT NULL,
 *   PRIMARY KEY (table_name, season)
 * );
 * </pre>
 */
@Mapper
public interface SeqCounterRepository {

	/**
	 * 指定テーブル×シーズンの番号を1つ進めて、進めた後の番号を返す。
	 *
	 * @param tableName テーブル名
	 * @param season シーズン
	 * @return 採番した番号（1 始まり）
	 */
	@Select({
			"INSERT INTO seq_counter (table_name, season, last_no,",
			"  register_id, register_time, update_id, update_time)",
			"VALUES (#{tableName}, #{season}, 1, 'SYSTEM', NOW(), 'SYSTEM', NOW())",
			"ON CONFLICT (table_name, season)",
			"DO UPDATE SET last_no = seq_counter.last_no + 1,",
			"  update_id = 'SYSTEM', update_time = NOW()",
			"RETURNING last_no"
	})
	@Options(flushCache = FlushCachePolicy.TRUE, useCache = false)
	long nextNumber(@Param("tableName") String tableName, @Param("season") String season);

}