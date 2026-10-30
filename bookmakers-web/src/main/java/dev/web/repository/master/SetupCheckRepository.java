package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 管理画面の未対応チェック（bm_a035）用リポジトリ（soccer_bm_master）
 * @author shiraishitoshio
 */
@Repository
public class SetupCheckRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public SetupCheckRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/**
	 * 新規登録で、まだ画面で確認していない国・リーグ（initial_reading_csv_master の initial_flg = '0'）
	 * @param masterName master_name（例: country_league_season_master）
	 * @return 「国 / リーグ」の一覧
	 */
	public List<String> findInitialPending(String masterName) {
		String sql = """
				SELECT country, league
				FROM initial_reading_csv_master
				WHERE master_name = :masterName
				  AND initial_flg = '0'
				ORDER BY country, league
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource("masterName", masterName),
				(rs, n) -> rs.getString("country") + " / " + rs.getString("league"));
	}

	/**
	 * チームの重複: 同じ国の有効なチーム（del_flg = '0'）で、リンクの「/team/xxx/yyy/」が同じ行が2行以上あるもの。
	 * リーグ所属チーム付け替え設定（LeagueTeamService）と同じ判定。
	 * @return 「国: チーム名 チームが Aリーグ と Bリーグ に重複しています。」の一覧
	 */
	public List<String> findTeamDuplicates() {
		String sql = """
				WITH t AS (
				  SELECT id, country, league, team,
				         lower(substring(link FROM '/team/[^/?#]+/[^/?#]+')) AS lk
				  FROM country_league_master
				  WHERE del_flg = '0'
				)
				SELECT country,
				       (array_agg(team ORDER BY id DESC))[1] AS team,
				       string_agg(league, ' と ' ORDER BY id) AS leagues
				FROM t
				WHERE lk IS NOT NULL
				GROUP BY country, lk
				HAVING COUNT(*) > 1
				ORDER BY country, lk
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(),
				(rs, n) -> rs.getString("country") + ": " + rs.getString("team") + " チームが "
						+ rs.getString("leagues") + " に重複しています。");
	}
}
