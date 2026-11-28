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
				+ " ORDER BY rank_no, s.team COLLATE \"ja-x-icu\"";
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
	 * 順位表（static_data の終了済の試合から計算。2026-11-28 追加）。
	 * 統計（surface_overview_match）がまだ無いリーグ用。トップ画面の W/D/L と同じく static_data を見る。
	 * <ul>
	 *   <li>対象: 直近 {@value #STATIC_WINDOW_DAYS} 日の、このリーグ・ラウンド番号のある試合。
	 *       「ラウンド 1」があれば、一番新しいラウンド 1 の 14 日前から（前のシーズンを混ぜない）。</li>
	 *   <li>1 試合 1 回（match_id、無ければ ホーム・アウェー・ラウンド）。終了済の行がある試合だけ。
	 *       スコアは同じ試合でスコアが入っている最後の行。</li>
	 *   <li>site_rank: チームの最新の行の順位（サイト表示）。</li>
	 * </ul>
	 * 取れていない試合があると勝ち点が実際より少なくなる（画面に注意書きを出す）。
	 */
	public List<StandingRowDTO> findStandingsFromStatic(String country, String league) {
		String sql = "WITH y AS ("
				+ " SELECT d.home_team_name, d.away_team_name, d.home_score, d.away_score,"
				+ "        CAST(d.home_rank AS text) AS home_rank, CAST(d.away_rank AS text) AS away_rank,"
				+ "        d.times, d.record_time,"
				+ "        NULLIF(SUBSTRING(d.seq_key FROM '([0-9]+)$'), '')::BIGINT AS seq_no,"
				+ "        NULLIF(SUBSTRING(d.data_category FROM 'ラウンド\\s*([0-9]+)'), '')::INTEGER AS round_no,"
				+ "        d.match_id"
				+ " FROM static_data d"
				+ " WHERE d.record_time >= CURRENT_TIMESTAMP - INTERVAL '" + STATIC_WINDOW_DAYS + " days'"
				+ "   AND normalize(BTRIM(split_part(d.data_category, ':', 1)), NFKC) = normalize(CAST(:country AS text), NFKC)"
				+ "   AND normalize(BTRIM(split_part(split_part(d.data_category, ':', 2), ' - ', 1)), NFKC)"
				+ "       = normalize(CAST(:league AS text), NFKC)"
				+ "), st AS ("
				+ " SELECT COALESCE(MAX(record_time) FILTER (WHERE round_no = 1) - INTERVAL '14 days',"
				+ "                 CURRENT_TIMESTAMP - INTERVAL '" + STATIC_WINDOW_DAYS + " days') AS from_time"
				+ " FROM y"
				+ "), z AS ("
				+ " SELECT y.*, COALESCE(NULLIF(BTRIM(y.match_id), ''),"
				+ "          " + key("y.home_team_name") + " || '|' || " + key("y.away_team_name") + " || '|' || y.round_no) AS mk"
				+ " FROM y, st"
				+ " WHERE y.round_no IS NOT NULL AND y.record_time >= st.from_time"
				+ "), m AS ("
				+ " SELECT DISTINCT ON (mk) mk, round_no, home_team_name, away_team_name, record_time,"
				+ "        BTRIM(home_score)::INTEGER AS hs, BTRIM(away_score)::INTEGER AS aws"
				+ " FROM z"
				+ " WHERE BTRIM(home_score) ~ '^[0-9]+$' AND BTRIM(away_score) ~ '^[0-9]+$'"
				+ "   AND mk IN (SELECT mk FROM z WHERE BTRIM(times) = '終了済')"
				+ " ORDER BY mk, seq_no DESC NULLS LAST, record_time DESC"
				+ "), t AS ("
				+ " SELECT " + key("home_team_name") + " AS tk, home_team_name AS team, hs AS gf, aws AS ga, round_no, record_time FROM m"
				+ " UNION ALL"
				+ " SELECT " + key("away_team_name") + ", away_team_name, aws, hs, round_no, record_time FROM m"
				+ "), r AS ("
				+ " SELECT DISTINCT ON (tk) tk, BTRIM(rk)::INTEGER AS site_rank FROM ("
				+ "   SELECT " + key("home_team_name") + " AS tk, home_rank AS rk, record_time, seq_no FROM z"
				+ "   UNION ALL"
				+ "   SELECT " + key("away_team_name") + ", away_rank, record_time, seq_no FROM z"
				+ " ) q WHERE BTRIM(rk) ~ '^[0-9]+$'"
				+ " ORDER BY tk, record_time DESC NULLS LAST, seq_no DESC NULLS LAST"
				+ "), s AS ("
				+ " SELECT tk AS team_key, MAX(team) AS team, COUNT(*)::INTEGER AS games,"
				+ "        COUNT(*) FILTER (WHERE gf > ga)::INTEGER AS win,"
				+ "        COUNT(*) FILTER (WHERE gf = ga)::INTEGER AS draw,"
				+ "        COUNT(*) FILTER (WHERE gf < ga)::INTEGER AS lose,"
				+ "        SUM(CASE WHEN gf > ga THEN 3 WHEN gf = ga THEN 1 ELSE 0 END)::INTEGER AS points,"
				+ "        SUM(gf)::INTEGER AS goals_for, SUM(ga)::INTEGER AS goals_against,"
				+ "        (ARRAY_AGG(CASE WHEN gf > ga THEN 'W' WHEN gf = ga THEN 'D' ELSE 'L' END"
				+ "                   ORDER BY round_no DESC, record_time DESC))[1:5] AS form"
				+ " FROM t GROUP BY tk"
				+ ")"
				+ " SELECT s.*, r.site_rank, (s.goals_for - s.goals_against) AS goal_diff,"
				+ "        RANK() OVER (ORDER BY s.points DESC, (s.goals_for - s.goals_against) DESC, s.goals_for DESC)::INTEGER AS rank_no"
				+ " FROM s LEFT JOIN r ON r.tk = s.team_key"
				+ " ORDER BY rank_no, s.team COLLATE \"ja-x-icu\"";
		return bmJdbcTemplate.query(sql, params(country, league, null, null), (rs, n) -> {
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

	/** static_data から順位表を作るときに見る日数 */
	static final int STATIC_WINDOW_DAYS = 300;

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
