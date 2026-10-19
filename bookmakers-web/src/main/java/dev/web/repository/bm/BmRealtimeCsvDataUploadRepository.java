package dev.web.repository.bm;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * リアルタイムデータ（static_data）の非表示・再表示（bm DB）
 *
 * <ul>
 *   <li>del_flg: '0' = 表示 / '1' = 非表示</li>
 *   <li>キー: ホームチーム + アウェーチーム + データカテゴリ（該当する行すべてが対象）。
 *       全角・半角の揺れと前後の空白は無視して比べる。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Repository
public class BmRealtimeCsvDataUploadRepository {

	/** 更新者 */
	public static final String CSV_USER = "CSV_UPLOAD";

	/** キーの条件 */
	private static final String KEY_WHERE = """
			 WHERE normalize(BTRIM(home_team_name), NFKC) = normalize(BTRIM(:home), NFKC)
			   AND normalize(BTRIM(away_team_name), NFKC) = normalize(BTRIM(:away), NFKC)
			   AND normalize(BTRIM(data_category), NFKC) = normalize(BTRIM(:category), NFKC)
			""";

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	/** bm DB 用のトランザクション */
	private final TransactionTemplate transactionTemplate;

	public BmRealtimeCsvDataUploadRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(
				new DataSourceTransactionManager(bmJdbcTemplate.getJdbcTemplate().getDataSource()));
	}

	/**
	 * 1 つのトランザクションで実行する（例外が出たら全部戻す）
	 * @param work 処理
	 */
	public void inTransaction(Runnable work) {
		transactionTemplate.executeWithoutResult(status -> work.run());
	}

	/** キーに該当する行数 */
	public int countByKey(String home, String away, String category) {
		String sql = "SELECT COUNT(*) FROM static_data" + KEY_WHERE;
		Integer cnt = bmJdbcTemplate.queryForObject(sql, keyParams(home, away, category), Integer.class);
		return cnt == null ? 0 : cnt;
	}

	/** キーに該当する行をまとめて非表示（'1'）/ 再表示（'0'） */
	public int updateDelFlgByKey(String home, String away, String category, String delFlg) {
		String sql = "UPDATE static_data SET del_flg = :delFlg, update_id = :user, update_time = CURRENT_TIMESTAMP"
				+ KEY_WHERE;
		return bmJdbcTemplate.update(sql, keyParams(home, away, category)
				.addValue("delFlg", delFlg)
				.addValue("user", CSV_USER));
	}

	private static MapSqlParameterSource keyParams(String home, String away, String category) {
		return new MapSqlParameterSource()
				.addValue("home", home)
				.addValue("away", away)
				.addValue("category", category);
	}
}
