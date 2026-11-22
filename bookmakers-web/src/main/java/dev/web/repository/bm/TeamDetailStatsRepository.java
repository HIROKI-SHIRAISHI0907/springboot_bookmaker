package dev.web.repository.bm;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.team.bm_w004.TeamDetailStatsResponse;

/**
 * チームの統計詳細データ（B016 が登録する match_detail_*）の集計（チームページ用。2026-11 追加）
 * <p>
 * 対象の試合 = その国・リーグの試合（static_data の data_category、または match_detail_match.category で判定）のうち、
 * ホームかアウェーがそのチーム。B013 がシーズン終了時に統計詳細データを消すので、残っているのは今季の試合。
 * 値は VARCHAR なので、先頭の数値を取り出して集計する（"35/40 (88%)" → 35）。
 * </p>
 * @author shiraishitoshio
 */
@Repository
public class TeamDetailStatsRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public TeamDetailStatsRepository(@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/** 文字列の先頭の数値（無ければ NULL） */
	private static String n(String col) {
		return "NULLIF(SUBSTRING(" + col + " FROM '-?[0-9]+(?:\\.[0-9]+)?'), '')::NUMERIC";
	}

	/** このチームの試合（match_id・試合日・チームの側 HOME / AWAY） */
	private static final String TEAM_MATCHES = """
			WITH lm AS (
			    SELECT DISTINCT d.match_id
			    FROM static_data d
			    WHERE NULLIF(BTRIM(d.match_id), '') IS NOT NULL
			      AND normalize(BTRIM(split_part(d.data_category, ':', 1)), NFKC) = normalize(:country, NFKC)
			      AND normalize(BTRIM(split_part(split_part(d.data_category, ':', 2), ' - ', 1)), NFKC) = normalize(:league, NFKC)
			    UNION
			    SELECT m.match_id
			    FROM match_detail_match m
			    WHERE normalize(COALESCE(m.category, ''), NFKC) LIKE normalize(:colonLike, NFKC) || '%'
			       OR normalize(COALESCE(m.category, ''), NFKC) LIKE '%' || normalize(:slashLike, NFKC) || '%'
			),
			tm AS (
			    SELECT m.match_id, m.match_date,
			           CASE WHEN normalize(m.home_team_name, NFKC) = normalize(:team, NFKC) THEN 'HOME' ELSE 'AWAY' END AS side
			    FROM match_detail_match m
			    WHERE m.match_id IN (SELECT match_id FROM lm)
			      AND (normalize(m.home_team_name, NFKC) = normalize(:team, NFKC)
			           OR normalize(m.away_team_name, NFKC) = normalize(:team, NFKC))
			)
			""";

	private static MapSqlParameterSource params(String country, String league, String team) {
		String colon = country + ": " + league;
		String slash = country + " / " + league;
		return new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league)
				.addValue("team", team)
				.addValue("colonLike", escapeLike(colon))
				.addValue("slashLike", escapeLike(slash));
	}

	private static String escapeLike(String s) {
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/** 試合数・最新の試合日・平均評価点・直近の監督 */
	public TeamDetailStatsResponse.Summary findSummary(String country, String league, String team) {
		String sql = TEAM_MATCHES + """
				SELECT (SELECT COUNT(*) FROM tm) AS match_count,
				       (SELECT to_char(MAX(match_date), 'YYYY-MM-DD') FROM tm) AS latest_date,
				       (SELECT ROUND(AVG(""" + n("f.average_rating") + """
				), 2)
				          FROM match_detail_formation f JOIN tm ON tm.match_id = f.match_id AND UPPER(f.side) = tm.side) AS avg_rating,
				       (SELECT f.coach_name
				          FROM match_detail_formation f JOIN tm ON tm.match_id = f.match_id AND UPPER(f.side) = tm.side
				         WHERE NULLIF(BTRIM(f.coach_name), '') IS NOT NULL
				         ORDER BY tm.match_date DESC NULLS LAST LIMIT 1) AS coach
				""";
		return bmJdbcTemplate.queryForObject(sql, params(country, league, team), (rs, i) -> {
			TeamDetailStatsResponse.Summary s = new TeamDetailStatsResponse.Summary();
			s.setMatchCount(rs.getInt("match_count"));
			s.setLatestMatchDate(rs.getString("latest_date"));
			s.setAverageRating(rs.getBigDecimal("avg_rating") == null ? null : rs.getBigDecimal("avg_rating").doubleValue());
			s.setCoach(rs.getString("coach"));
			return s;
		});
	}

	/** フォーメーションの使用回数（多い順） */
	public List<TeamDetailStatsResponse.Formation> findFormations(String country, String league, String team) {
		String sql = TEAM_MATCHES + """
				SELECT BTRIM(f.formation) AS formation, COUNT(*) AS cnt
				FROM match_detail_formation f JOIN tm ON tm.match_id = f.match_id AND UPPER(f.side) = tm.side
				WHERE NULLIF(BTRIM(f.formation), '') IS NOT NULL
				GROUP BY BTRIM(f.formation)
				ORDER BY cnt DESC, formation
				""";
		return bmJdbcTemplate.query(sql, params(country, league, team), (rs, i) -> {
			TeamDetailStatsResponse.Formation f = new TeamDetailStatsResponse.Formation();
			f.setFormation(rs.getString("formation"));
			f.setCount(rs.getInt("cnt"));
			return f;
		});
	}

	/** 選手ごとの集計（選手スタッツ match_detail_member_stat。出場数の多い順） */
	public List<TeamDetailStatsResponse.Player> findPlayers(String country, String league, String team) {
		String sql = TEAM_MATCHES + """
				SELECT COALESCE(NULLIF(BTRIM(s.player_id), ''), BTRIM(s.player_name)) AS pkey,
				       (ARRAY_AGG(s.player_name ORDER BY tm.match_date DESC NULLS LAST))[1] AS player_name,
				       (ARRAY_AGG(s.position ORDER BY tm.match_date DESC NULLS LAST))[1] AS position,
				       COUNT(*) AS apps,
				       SUM(""" + n("s.match_minutes_played") + """
				) AS minutes,
				       ROUND(AVG(""" + n("s.fs_rating") + """
				), 2) AS rating,
				       SUM(""" + n("s.goals") + """
				) AS goals,
				       SUM(""" + n("s.assists_goal") + """
				) AS assists,
				       ROUND(SUM(""" + n("s.expected_goals") + """
				), 2) AS xg,
				       ROUND(SUM(""" + n("s.expected_assists") + """
				), 2) AS xa,
				       SUM(""" + n("s.shots_total") + """
				) AS shots,
				       SUM(""" + n("s.shots_on_target") + """
				) AS shots_on_target,
				       SUM(""" + n("s.key_passes") + """
				) AS key_passes,
				       SUM(""" + n("s.cards_yellow") + """
				) AS yellow,
				       SUM(""" + n("s.cards_red") + """
				) AS red,
				       SUM(""" + n("s.saves_total") + """
				) AS saves
				FROM match_detail_member_stat s JOIN tm ON tm.match_id = s.match_id AND UPPER(s.side) = tm.side
				WHERE NULLIF(BTRIM(s.player_name), '') IS NOT NULL
				GROUP BY pkey
				ORDER BY apps DESC, minutes DESC NULLS LAST, player_name COLLATE "ja-x-icu"
				""";
		return bmJdbcTemplate.query(sql, params(country, league, team), (rs, i) -> {
			TeamDetailStatsResponse.Player p = new TeamDetailStatsResponse.Player();
			p.setName(rs.getString("player_name"));
			p.setPosition(rs.getString("position"));
			p.setApps(rs.getInt("apps"));
			p.setMinutes(intOrNull(rs.getBigDecimal("minutes")));
			p.setRating(dblOrNull(rs.getBigDecimal("rating")));
			p.setGoals(intOrNull(rs.getBigDecimal("goals")));
			p.setAssists(intOrNull(rs.getBigDecimal("assists")));
			p.setXg(dblOrNull(rs.getBigDecimal("xg")));
			p.setXa(dblOrNull(rs.getBigDecimal("xa")));
			p.setShots(intOrNull(rs.getBigDecimal("shots")));
			p.setShotsOnTarget(intOrNull(rs.getBigDecimal("shots_on_target")));
			p.setKeyPasses(intOrNull(rs.getBigDecimal("key_passes")));
			p.setYellowCards(intOrNull(rs.getBigDecimal("yellow")));
			p.setRedCards(intOrNull(rs.getBigDecimal("red")));
			p.setSaves(intOrNull(rs.getBigDecimal("saves")));
			return p;
		});
	}

	private static Integer intOrNull(java.math.BigDecimal v) {
		return v == null ? null : v.intValue();
	}

	private static Double dblOrNull(java.math.BigDecimal v) {
		return v == null ? null : v.doubleValue();
	}
}
