package dev.web.repository.bm;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.upcomingDetail.upcomingDetailDTO.GoalStatsDTO;
import dev.web.api.upcomingDetail.upcomingDetailDTO.StandingRowDTO;

/**
 * これからの試合の詳細（soccer_bm: surface_overview_match）
 *
 * <ul>
 *   <li>{@link #findLatestSeason}: 国・リーグの最新シーズン</li>
 *   <li>{@link #findTeamLatestLeague}: チームの最後の試合のシーズン・国・リーグ（試合のリーグにデータが無いとき用）</li>
 *   <li>{@link #findStandings}: 順位表（勝ち点 → 得失点差 → 得点）</li>
 *   <li>{@link #findGoalStats}: チームの統計（全試合 / ホーム時 / アウェー時）</li>
 * </ul>
 * 国・リーグ・チーム名は前後空白を除いて NFKC 正規化して比べる。
 * @author shiraishitoshio
 */
@Repository
public class UpcomingDetailRepository {

	/** 比較用のキー（SQL） */
	private static String key(String expr) {
		return "normalize(BTRIM(" + expr + "), NFKC)";
	}

	private static final String WHERE_LEAGUE = " WHERE season = :season"
			+ "   AND " + key("country") + " = " + key("CAST(:country AS text)")
			+ "   AND " + key("league") + " = " + key("CAST(:league AS text)");

	/** 直近の順（form 用） */
	private static final String RECENT = "ORDER BY round_no DESC, match_time DESC NULLS LAST";

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public UpcomingDetailRepository(@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/** 国・リーグの最新シーズン（無ければ null） */
	public String findLatestSeason(String country, String league) {
		String sql = "SELECT MAX(season) FROM surface_overview_match"
				+ " WHERE " + key("country") + " = " + key("CAST(:country AS text)")
				+ "   AND " + key("league") + " = " + key("CAST(:league AS text)");
		return bmJdbcTemplate.queryForObject(sql, params(country, league, null, null), String.class);
	}

	/** チームの最後の試合の {シーズン, 国, リーグ}（無ければ null） */
	public String[] findTeamLatestLeague(String team) {
		String sql = "SELECT season, country, league FROM surface_overview_match"
				+ " WHERE " + key("team") + " = " + key("CAST(:team AS text)")
				+ " ORDER BY match_time DESC NULLS LAST, season DESC, round_no DESC"
				+ " LIMIT 1";
		List<String[]> rows = bmJdbcTemplate.query(sql, params(null, null, null, team),
				(rs, n) -> new String[] { rs.getString("season"), rs.getString("country"), rs.getString("league") });
		return rows.isEmpty() ? null : rows.get(0);
	}

	/** 順位表（順位順） */
	public List<StandingRowDTO> findStandings(String country, String league, String season) {
		String sql = "WITH s AS ("
				+ " SELECT " + key("team") + " AS team_key,"
				+ "        MAX(team) AS team,"
				+ "        COUNT(*)::INTEGER AS games,"
				+ "        COUNT(*) FILTER (WHERE result = 'W')::INTEGER AS win,"
				+ "        COUNT(*) FILTER (WHERE result = 'D')::INTEGER AS draw,"
				+ "        COUNT(*) FILTER (WHERE result = 'L')::INTEGER AS lose,"
				+ "        COALESCE(SUM(points), 0)::INTEGER AS points,"
				+ "        COALESCE(SUM(goals_for), 0)::INTEGER AS goals_for,"
				+ "        COALESCE(SUM(goals_against), 0)::INTEGER AS goals_against,"
				+ "        (ARRAY_AGG(result::text " + RECENT + "))[1:5] AS form,"
				+ "        (ARRAY_AGG(team_rank " + RECENT + ") FILTER (WHERE team_rank IS NOT NULL))[1] AS site_rank"
				+ " FROM surface_overview_match"
				+ WHERE_LEAGUE
				+ " GROUP BY " + key("team")
				+ ")"
				+ " SELECT s.*, (s.goals_for - s.goals_against) AS goal_diff,"
				+ "        RANK() OVER (ORDER BY s.points DESC, (s.goals_for - s.goals_against) DESC, s.goals_for DESC)::INTEGER AS rank_no"
				+ " FROM s"
				+ " ORDER BY rank_no, s.team";
		return bmJdbcTemplate.query(sql, params(country, league, season, null), (rs, n) -> {
			StandingRowDTO r = new StandingRowDTO();
			r.setRank(rs.getInt("rank_no"));
			r.setSiteRank((Integer) rs.getObject("site_rank", Integer.class));
			r.setTeam(rs.getString("team"));
			r.setGames(rs.getInt("games"));
			r.setWin(rs.getInt("win"));
			r.setDraw(rs.getInt("draw"));
			r.setLose(rs.getInt("lose"));
			r.setPoints(rs.getInt("points"));
			r.setGoalsFor(rs.getInt("goals_for"));
			r.setGoalsAgainst(rs.getInt("goals_against"));
			r.setGoalDiff(rs.getInt("goal_diff"));
			r.setForm(toList(rs.getArray("form")));
			return r;
		});
	}

	/**
	 * チームの統計（そのシーズン・リーグ）。scope = ALL / HOME / AWAY の 3 行（試合が無い区分は games = 0）。
	 * 返す順番は ALL, HOME, AWAY とは限らない（scope で見分ける）。
	 */
	public List<GoalStatsDTO> findGoalStats(String country, String league, String season, String team) {
		String sql = "SELECT"
				+ "  CASE WHEN GROUPING(ha) = 1 THEN 'ALL' WHEN ha = 'H' THEN 'HOME' ELSE 'AWAY' END AS scope,"
				+ "  COUNT(*)::INTEGER AS games,"
				+ "  COUNT(*) FILTER (WHERE result = 'W')::INTEGER AS win,"
				+ "  COUNT(*) FILTER (WHERE result = 'D')::INTEGER AS draw,"
				+ "  COUNT(*) FILTER (WHERE result = 'L')::INTEGER AS lose,"
				+ "  COALESCE(SUM(points), 0)::INTEGER AS points,"
				+ "  COALESCE(SUM(goals_for), 0)::INTEGER AS goals_for,"
				+ "  COALESCE(SUM(goals_against), 0)::INTEGER AS goals_against,"
				+ "  ROUND(AVG(goals_for), 2) AS avg_goals_for,"
				+ "  ROUND(AVG(goals_against), 2) AS avg_goals_against,"
				+ "  COUNT(*) FILTER (WHERE goals_for_1st IS NOT NULL AND goals_against_1st IS NOT NULL)::INTEGER AS half_known,"
				+ "  COALESCE(SUM(goals_for_1st)     FILTER (WHERE goals_for_1st IS NOT NULL AND goals_against_1st IS NOT NULL), 0)::INTEGER AS gf1,"
				+ "  COALESCE(SUM(goals_for_2nd)     FILTER (WHERE goals_for_1st IS NOT NULL AND goals_against_1st IS NOT NULL), 0)::INTEGER AS gf2,"
				+ "  COALESCE(SUM(goals_against_1st) FILTER (WHERE goals_for_1st IS NOT NULL AND goals_against_1st IS NOT NULL), 0)::INTEGER AS ga1,"
				+ "  COALESCE(SUM(goals_against_2nd) FILTER (WHERE goals_for_1st IS NOT NULL AND goals_against_1st IS NOT NULL), 0)::INTEGER AS ga2,"
				+ "  COUNT(*) FILTER (WHERE goals_against = 0)::INTEGER AS clean_sheets,"
				+ "  COUNT(*) FILTER (WHERE goals_for = 0)::INTEGER AS failed_to_score,"
				+ "  COUNT(*) FILTER (WHERE goals_for > 0 AND goals_against > 0)::INTEGER AS both_scored,"
				+ "  COUNT(*) FILTER (WHERE goals_for + goals_against >= 2)::INTEGER AS over15,"
				+ "  COUNT(*) FILTER (WHERE goals_for + goals_against >= 3)::INTEGER AS over25,"
				+ "  COUNT(*) FILTER (WHERE goals_for + goals_against >= 4)::INTEGER AS over35,"
				+ "  COUNT(*) FILTER (WHERE first_goal = 'T')::INTEGER AS scored_first,"
				+ "  COUNT(*) FILTER (WHERE first_goal = 'O')::INTEGER AS conceded_first,"
				+ "  (ARRAY_AGG(result::text " + RECENT + "))[1:5] AS form"
				+ " FROM surface_overview_match"
				+ WHERE_LEAGUE
				+ "   AND " + key("team") + " = " + key("CAST(:team AS text)")
				+ " GROUP BY GROUPING SETS ((), (ha))";
		return bmJdbcTemplate.query(sql, params(country, league, season, team), (rs, n) -> toStats(rs));
	}

	private static GoalStatsDTO toStats(ResultSet rs) throws SQLException {
		GoalStatsDTO d = new GoalStatsDTO();
		d.setScope(rs.getString("scope"));
		d.setGames(rs.getInt("games"));
		d.setWin(rs.getInt("win"));
		d.setDraw(rs.getInt("draw"));
		d.setLose(rs.getInt("lose"));
		d.setPoints(rs.getInt("points"));
		d.setGoalsFor(rs.getInt("goals_for"));
		d.setGoalsAgainst(rs.getInt("goals_against"));
		d.setAvgGoalsFor(toDouble(rs.getBigDecimal("avg_goals_for")));
		d.setAvgGoalsAgainst(toDouble(rs.getBigDecimal("avg_goals_against")));
		d.setHalfKnownGames(rs.getInt("half_known"));
		d.setGoalsFor1st(rs.getInt("gf1"));
		d.setGoalsFor2nd(rs.getInt("gf2"));
		d.setGoalsAgainst1st(rs.getInt("ga1"));
		d.setGoalsAgainst2nd(rs.getInt("ga2"));
		d.setCleanSheets(rs.getInt("clean_sheets"));
		d.setFailedToScore(rs.getInt("failed_to_score"));
		d.setBothScored(rs.getInt("both_scored"));
		d.setOver15(rs.getInt("over15"));
		d.setOver25(rs.getInt("over25"));
		d.setOver35(rs.getInt("over35"));
		d.setScoredFirst(rs.getInt("scored_first"));
		d.setConcededFirst(rs.getInt("conceded_first"));
		d.setForm(toList(rs.getArray("form")));
		return d;
	}

	private static Double toDouble(BigDecimal v) {
		return v == null ? null : v.doubleValue();
	}

	private static List<String> toList(Array a) throws SQLException {
		List<String> list = new ArrayList<>();
		if (a == null) {
			return list;
		}
		Object o = a.getArray();
		if (o instanceof Object[]) {
			for (Object x : (Object[]) o) {
				if (x != null) {
					list.add(x.toString().trim());
				}
			}
		}
		return list;
	}

	private static MapSqlParameterSource params(String country, String league, String season, String team) {
		return new MapSqlParameterSource()
				.addValue("country", country == null ? "" : country)
				.addValue("league", league == null ? "" : league)
				.addValue("season", season == null ? "" : season)
				.addValue("team", team == null ? "" : team);
	}
}
