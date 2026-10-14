package dev.batch.repository.bm;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Options.FlushCachePolicy;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 採番カウンタ（seq_counter）Mapper（batch 用）。
 *
 * <p>application 側の SeqCounterRepository#nextNumber と同じ SQL。
 * 「テーブル名 × シーズン（ここでは発生年）」の番号を1つ進めて返す（無ければ 1 で作成）。</p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>MyBatis のローカルキャッシュ</b>: 同じトランザクションで同じ引数を2回呼ぶと同じ番号が返るため、
 *       {@code flushCache = TRUE} / {@code useCache = false} を付けている。</li>
 *   <li><b>行ロック</b>: 呼び出し元のトランザクションが終わるまで、その行はロックされる
 *       （AnalyzeErrorWriter は REQUIRES_NEW なのですぐに終わる）。</li>
 * </ul>
 */
@Mapper
public interface SeqCounterBatchRepository {

	/**
	 * 指定テーブル×シーズンの番号を1つ進めて、進めた後の番号を返す。
	 *
	 * @param tableName テーブル名
	 * @param season シーズン（analyze_error_match は発生年）
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
