package dev.web.repository.master;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import dev.common.util.DateOffsetDecisionUtil;
import lombok.Data;

/**
 * サブリーグ保有国リーグ登録設定（soccer_bm_master。2026-11-09 追加）
 *
 * <ul>
 *   <li>country_league_sub_league_master: 国・リーグ ごとに、サブリーグ（アペルトゥーラ／クラウスラ など）でシーズンが分かれているか
 *       （sub_league_flg = '1' あり / '0' なし）。行が無い 国・リーグ は未設定。</li>
 *   <li>country_league_sub_league_name_master: サブリーグありの 国・リーグ のサブリーグ名（disp_order 順）。</li>
 *   <li>対象の 国・リーグ は country_league_season_master（del_flg = '0'）にあるもの。</li>
 * </ul>
 * 期間はシーズン期間（country_league_season_master）で管理するので、ここでは持たない。
 * チームのサブリーグ割り当て（country_league_master.sub_league。サブリーグ名反映設定）とは別のもの。
 * @author shiraishitoshio
 */
@Repository
public class SubLeagueSeasonRepository {

	/** サブリーグあり */
	public static final String FLG_HAS = "1";

	/** サブリーグなし */
	public static final String FLG_NONE = "0";

	/** 国・リーグ ごとの設定 */
	@Data
	public static class Setting {
		private String country;
		private String league;
		/** '1' あり / '0' なし */
		private String subLeagueFlg;
		/** 日本時間 "yyyy-MM-dd HH:mm:ss" */
		private String updateTime;
		private String updateId;
	}

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	/** webMaster DB 用のトランザクション */
	private final TransactionTemplate transactionTemplate;

	public SubLeagueSeasonRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(
				new DataSourceTransactionManager(masterJdbcTemplate.getJdbcTemplate().getDataSource()));
	}

	/** 1 つのトランザクションで実行する（例外が出たら全部戻す） */
	public void inTransaction(Runnable work) {
		transactionTemplate.executeWithoutResult(status -> work.run());
	}

	/**
	 * 対象の 国・リーグ（country_league_season_master の del_flg = '0'。国 → リーグ の順）
	 * @return [country, league]
	 */
	public List<String[]> findTargets() {
		String sql = """
				SELECT DISTINCT country, league
				FROM country_league_season_master
				WHERE del_flg = '0'
				ORDER BY country, league
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(),
				(rs, n) -> new String[] { rs.getString("country"), rs.getString("league") });
	}

	/** 対象の 国・リーグ か */
	public boolean existsTarget(String country, String league) {
		String sql = """
				SELECT EXISTS (
				    SELECT 1 FROM country_league_season_master
				    WHERE country = :country AND league = :league AND del_flg = '0'
				)
				""";
		Boolean b = masterJdbcTemplate.queryForObject(sql, params(country, league), Boolean.class);
		return Boolean.TRUE.equals(b);
	}

	/**
	 * 設定済みの 国・リーグ
	 * @return key = country + "||" + league
	 */
	public Map<String, Setting> findSettings() {
		String sql = """
				SELECT country, league, sub_league_flg, update_id, update_time
				FROM country_league_sub_league_master
				""";
		Map<String, Setting> map = new LinkedHashMap<>();
		masterJdbcTemplate.query(sql, new MapSqlParameterSource(), rs -> {
			Setting s = new Setting();
			s.setCountry(rs.getString("country"));
			s.setLeague(rs.getString("league"));
			s.setSubLeagueFlg(rs.getString("sub_league_flg"));
			s.setUpdateId(rs.getString("update_id"));
			s.setUpdateTime(DateOffsetDecisionUtil.getJstLocalDateTimeString(rs, "update_time"));
			map.put(key(s.getCountry(), s.getLeague()), s);
		});
		return map;
	}

	/**
	 * サブリーグ名（disp_order 順）
	 * @return key = country + "||" + league
	 */
	public Map<String, List<String>> findNames() {
		String sql = """
				SELECT country, league, sub_league
				FROM country_league_sub_league_name_master
				ORDER BY country, league, disp_order, id
				""";
		Map<String, List<String>> map = new LinkedHashMap<>();
		masterJdbcTemplate.query(sql, new MapSqlParameterSource(), rs -> {
			map.computeIfAbsent(key(rs.getString("country"), rs.getString("league")), k -> new ArrayList<>())
					.add(rs.getString("sub_league"));
		});
		return map;
	}

	/**
	 * 未設定の 国・リーグ（未対応チェック用。「国 / リーグ」の形）
	 */
	public List<String> findUnsetLabels() {
		String sql = """
				SELECT s.country || ' / ' || s.league AS label
				FROM (
				    SELECT DISTINCT country, league
				    FROM country_league_season_master
				    WHERE del_flg = '0'
				) s
				WHERE NOT EXISTS (
				    SELECT 1 FROM country_league_sub_league_master m
				    WHERE m.country = s.country AND m.league = s.league
				)
				ORDER BY s.country, s.league
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> rs.getString("label"));
	}

	/**
	 * 国・リーグ の設定を登録・更新する（サブリーグ名は入れ直す）
	 * @param names サブリーグありのときのサブリーグ名（表示順）。なしのときは空
	 */
	public void save(String country, String league, String flg, List<String> names, String operatorId) {
		String upsert = """
				INSERT INTO country_league_sub_league_master (
				    country, league, sub_league_flg, register_id, register_time, update_id, update_time
				) VALUES (
				    :country, :league, :flg, :user, CURRENT_TIMESTAMP, :user, CURRENT_TIMESTAMP
				)
				ON CONFLICT (country, league) DO UPDATE SET
				    sub_league_flg = EXCLUDED.sub_league_flg,
				    update_id      = EXCLUDED.update_id,
				    update_time    = CURRENT_TIMESTAMP
				""";
		masterJdbcTemplate.update(upsert, params(country, league)
				.addValue("flg", flg)
				.addValue("user", operatorId));

		masterJdbcTemplate.update(
				"DELETE FROM country_league_sub_league_name_master WHERE country = :country AND league = :league",
				params(country, league));

		String insert = """
				INSERT INTO country_league_sub_league_name_master (
				    country, league, sub_league, disp_order, register_id, register_time, update_id, update_time
				) VALUES (
				    :country, :league, :subLeague, :dispOrder, :user, CURRENT_TIMESTAMP, :user, CURRENT_TIMESTAMP
				)
				""";
		int order = 0;
		for (String name : names) {
			order++;
			masterJdbcTemplate.update(insert, params(country, league)
					.addValue("subLeague", name)
					.addValue("dispOrder", order)
					.addValue("user", operatorId));
		}
	}

	/**
	 * 未設定の 国・リーグ を全部「サブリーグなし」にする（設定しない）
	 * @return 「なし」にした 国・リーグ の数
	 */
	public int saveUnsetAsNone(String operatorId) {
		String sql = """
				INSERT INTO country_league_sub_league_master (
				    country, league, sub_league_flg, register_id, register_time, update_id, update_time
				)
				SELECT DISTINCT s.country, s.league, :flg, :user, CURRENT_TIMESTAMP, :user, CURRENT_TIMESTAMP
				FROM country_league_season_master s
				WHERE s.del_flg = '0'
				  AND NOT EXISTS (
				      SELECT 1 FROM country_league_sub_league_master m
				      WHERE m.country = s.country AND m.league = s.league
				  )
				ON CONFLICT (country, league) DO NOTHING
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("flg", FLG_NONE)
				.addValue("user", operatorId));
	}

	public static String key(String country, String league) {
		return country + "||" + league;
	}

	private static MapSqlParameterSource params(String country, String league) {
		return new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league);
	}
}
