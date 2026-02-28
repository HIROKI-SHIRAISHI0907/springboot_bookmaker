package dev.web.repository.bm;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.Data;

/**
 * LeaguesRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class LeaguesRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public LeaguesRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	// --- Row 用の内部クラス（DB そのまま） ---
	@Data
	public static class LeagueCountRow {
		public String country;
		/** 親リーグ名（サイドメニュー表示用）例: "J2・J3リーグ" */
		public String leagueGroup;
		/** フルリーグ名（詳細画面表示用）例: "J2・J3リーグ - WEST A"（親集約の時はnullでもOK） */
		public String leagueFull;
		public String seasonYear;
		public String startSeasonDate;
		public String endSeasonDate;
		public Long teamCount;
		/** サブリーグの数（親集約行のときだけ意味がある。子のときはnull or 1） */
		public Long variantCount;
		public String path;
	}

	@Data
	public static class TeamRow {
		public Integer id;
		public String country;
		public String league;
		public String team;
		public String link;
	}

	private static final Pattern TEAM_LINK_PATTERN = Pattern.compile("^/team/([^/]+)/([^/]+)",
			Pattern.CASE_INSENSITIVE);

	// Node の toPath と同様: trim → 空白を1つに → encodeURIComponent 相当
	public String toPath(String s) {
		if (s == null)
			return "";
		String trimmed = s.trim().replaceAll("\\s+", " ");
		String enc = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
		// encodeURIComponent っぽく "+" を "%20" に
		return enc.replace("+", "%20");
	}

	public String[] parseTeamLink(String link) {
		if (link == null)
			return new String[] { "", "" };
		Matcher m = TEAM_LINK_PATTERN.matcher(link);
		if (!m.find())
			return new String[] { "", "" };
		return new String[] { m.group(1), m.group(2) };
	}

	// --- クエリ ---

	public List<LeagueCountRow> findLeagueCounts() {

		String sql = """
				  WITH base AS (
				    SELECT
				      a.country,
				      regexp_replace(a.league, '\\s*[-－—]\\s*.*$', '') AS league_group,
				      s.path AS routing_path,
				      s.season_year,
				      s.start_season_date,
				      s.end_season_date,
				      COUNT(c.id) AS team_count
				    FROM all_league_scrape_master a
				    JOIN country_league_season_master s
				      ON s.country = a.country
				     AND s.league  = a.league
				     AND s.valid_flg = '0'
				     AND s.del_flg   = '0'
				    LEFT JOIN country_league_master c
				      ON c.country = a.country
				     AND c.league  = a.league
				     AND c.del_flg = '0'
				    WHERE a.disp_flg = '0'
				      AND a.logic_flg = '0'
				    GROUP BY
				      a.country,
				      a.league,
				      s.path,
				      s.season_year,
				      s.start_season_date,
				      s.end_season_date
				  )
				  SELECT
				    country,
				    league_group,
				    MIN(routing_path) AS routing_path,
				    SUM(team_count)   AS team_count,
				    COUNT(*)          AS variant_count,
				    MAX(season_year)  AS season_year,
				    MIN(start_season_date) AS start_season_date,
				    MAX(end_season_date)   AS end_season_date
				  FROM base
				  GROUP BY country, league_group
				  ORDER BY country, league_group
				""";

		return masterJdbcTemplate.query(sql, new BeanPropertyRowMapper<>(LeagueCountRow.class));
	}

	/** 国＋リーグのチーム一覧 */
	public List<TeamRow> findTeamsInLeague(String country, String league) {
		String sql = """
				SELECT id, country, league, team, link
				FROM country_league_master
				WHERE country = :country AND league = :league
				ORDER BY team
				""";
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league);

		return masterJdbcTemplate.query(
				sql,
				params,
				new BeanPropertyRowMapper<>(TeamRow.class));
	}

	/** 国＋リーグのチーム一覧(英語名) */
	public List<TeamRow> findTeamsInLeagueOnSlug(String country, String league) {
		String sql = """
				SELECT clm.id, clm.country, clm.league, clm.team, clm.link
				FROM country_league_master clm
				INNER JOIN country_league_season_master clsm
				  ON clm.country = clsm.country
				 AND clm.league  = clsm.league
				 WHERE clsm.path = :path
				ORDER BY clm.team
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("path", "/soccer/" + country + "/" + league + "/");

		return masterJdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(TeamRow.class));
	}

	/** 指定チーム詳細 (1件のみ) teamEnglish + teamHash */
	public TeamRow findTeamDetailByTeamAndHash(String teamEnglish, String teamHash) {
		String sql = """
				SELECT id, country, league, team, link
				FROM country_league_master
				WHERE link = :link
				LIMIT 1
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("link", "/team/" + teamEnglish + "/" + teamHash + "/");

		List<TeamRow> list = masterJdbcTemplate.query(
				sql,
				params,
				new BeanPropertyRowMapper<>(TeamRow.class));
		return list.isEmpty() ? null : list.get(0);
	}
}
