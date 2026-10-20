package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import dev.web.api.bm_a033.LeagueLevelDTO;

/**
 * リーグレベル情報反映設定（soccer_bm_master: country_league_master.league_level）
 *
 * <ul>
 *   <li>{@link #findAll}: 国・リーグ ごとのレベル（行ごとにばらついていれば mixed）</li>
 *   <li>{@link #exists}: 国・リーグ があるか</li>
 *   <li>{@link #updateLevel}: 国・リーグ の全行（削除・非表示の行も）のレベルを更新</li>
 * </ul>
 * @author shiraishitoshio
 */
@Repository
public class LeagueLevelWebRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	/** webMaster DB 用のトランザクション（DataSource が複数あるため、この JdbcTemplate の DataSource で作る） */
	private final TransactionTemplate transactionTemplate;

	public LeagueLevelWebRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(
				new DataSourceTransactionManager(masterJdbcTemplate.getJdbcTemplate().getDataSource()));
	}

	/** 1 つのトランザクションで実行する（例外が出たら全部戻す） */
	public void inTransaction(Runnable work) {
		transactionTemplate.executeWithoutResult(status -> work.run());
	}

	/** 国・リーグ ごとのレベル（国 → レベル（未設定は最後）→ リーグ の順） */
	public List<LeagueLevelDTO> findAll() {
		String sql = """
				SELECT
				  country,
				  league,
				  MIN(league_level)                                   AS min_level,
				  COUNT(DISTINCT COALESCE(league_level, 0)) > 1       AS mixed,
				  COUNT(*) FILTER (WHERE del_flg = '0')::INTEGER      AS active_teams,
				  COUNT(*)::INTEGER                                   AS total_rows,
				  TO_CHAR(MAX(update_time) AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD HH24:MI:SS') AS update_time,
				  (ARRAY_AGG(update_id ORDER BY update_time DESC NULLS LAST))[1] AS update_id
				FROM country_league_master
				GROUP BY country, league
				ORDER BY country, MIN(league_level) NULLS LAST, league
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			LeagueLevelDTO d = new LeagueLevelDTO();
			d.setCountry(rs.getString("country"));
			d.setLeague(rs.getString("league"));
			d.setMixed(rs.getBoolean("mixed"));
			Integer level = (Integer) rs.getObject("min_level", Integer.class);
			// ばらついているときは「未設定」として見せる（保存でそろえてもらう）
			d.setLeagueLevel(d.isMixed() ? null : level);
			d.setActiveTeams(rs.getInt("active_teams"));
			d.setTotalRows(rs.getInt("total_rows"));
			d.setUpdateTime(rs.getString("update_time"));
			d.setUpdateId(rs.getString("update_id"));
			return d;
		});
	}

	/** 国・リーグ があるか */
	public boolean exists(String country, String league) {
		String sql = "SELECT EXISTS (SELECT 1 FROM country_league_master WHERE country = :country AND league = :league)";
		Boolean b = masterJdbcTemplate.queryForObject(sql, params(country, league), Boolean.class);
		return Boolean.TRUE.equals(b);
	}

	/**
	 * 国・リーグ の全行のレベルを更新（値が変わる行だけ）
	 * @return 更新した行数
	 */
	public int updateLevel(String country, String league, Integer level, String updateId) {
		String sql = """
				UPDATE country_league_master
				   SET league_level = CAST(:level AS SMALLINT),
				       update_id    = :updateId,
				       update_time  = CURRENT_TIMESTAMP
				 WHERE country = :country
				   AND league  = :league
				   AND league_level IS DISTINCT FROM CAST(:level AS SMALLINT)
				""";
		MapSqlParameterSource p = params(country, league)
				.addValue("level", level)
				.addValue("updateId", updateId);
		return masterJdbcTemplate.update(sql, p);
	}

	private static MapSqlParameterSource params(String country, String league) {
		return new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league);
	}
}
