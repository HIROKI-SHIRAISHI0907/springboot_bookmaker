package dev.web.repository.bm;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 試合予定の詳細のアイコン用（soccer_bm。2026-11-27 追加）
 * <ul>
 *   <li>{@link #findMatches}: チームのシーズンの試合（surface_overview_match。B006 の BM_M031。ラウンド番号のある試合）</li>
 *   <li>{@link #findMetric}: リーグ内の順位を付ける数値（チーム × 値 × 試合数）。統計のビュー（ha = '*'）から</li>
 *   <li>{@link #findStyleLabel}: チームのスタイル（team_style_profile.style_label）</li>
 * </ul>
 * 国・リーグ・チーム名は前後空白を除いて NFKC 正規化して比べる（UpcomingDetailRepository と同じ）。
 * @author shiraishitoshio
 */
@Slf4j
@Repository
public class UpcomingBadgeRepository {

	/** 1 試合（チーム視点） */
	@Data
	public static class MatchRow {
		private int roundNo;
		private String ha;
		private String opponent;
		private String result;
		private int goalsFor;
		private int goalsAgainst;
		private String phase;
		private String firstGoal;
		private boolean flowKnown;
		private boolean everTrailed;
		private Timestamp matchTime;
	}

	/** リーグ内の数値 1 チーム分 */
	@Data
	public static class MetricRow {
		private String team;
		private BigDecimal value;
		private int matchCount;
	}

	/**
	 * リーグ内の順位を付ける数値の定義。
	 * sql は「team・value・match_count」を返す。:season / :country / :league を使う。
	 */
	public enum Metric {
		GOALS_FOR("SELECT MAX(team) AS team, AVG(goals_for)::NUMERIC AS value, COUNT(*)::INTEGER AS match_count"
				+ " FROM surface_overview_match" + Where.LEAGUE + " GROUP BY " + Where.key("team")),
		GOALS_AGAINST("SELECT MAX(team) AS team, AVG(goals_against)::NUMERIC AS value, COUNT(*)::INTEGER AS match_count"
				+ " FROM surface_overview_match" + Where.LEAGUE + " GROUP BY " + Where.key("team")),
		POSSESSION(Where.view("team_style_profile", "possession", "sample_match_count")),
		PASS_ACCURACY(Where.view("team_style_profile", "pass_accuracy", "sample_match_count")),
		SHOTS(Where.view("team_attack_stats", "shots_per90", "match_count")),
		SHOTS_ON_TARGET(Where.view("team_attack_stats", "shots_on_target_per90", "match_count")),
		CORNERS(Where.view("team_attack_stats", "corners_per90", "match_count")),
		XG(Where.view("team_efficiency_stats", "CASE WHEN match_count > 0 THEN xg / match_count END", "match_count")),
		FINISHING(Where.view("team_efficiency_stats", "shot_to_goal_rate", "match_count")),
		SHOTS_CONCEDED(Where.view("team_defense_stats", "shots_conceded_per90", "match_count")),
		SAVE_RATE(Where.view("team_defense_stats", "save_rate", "match_count")),
		SCORED_FIRST(Where.view("team_goal_timing_stats", "scored_first_rate", "match_count")),
		LATE_GOALS(Where.view("team_goal_timing_stats", "CASE WHEN match_count > 0 THEN late_goals::NUMERIC / match_count END", "match_count")),
		LATE_CONCEDED(Where.view("team_goal_timing_stats", "CASE WHEN match_count > 0 THEN late_goals_conceded::NUMERIC / match_count END", "match_count"));

		private final String sql;

		Metric(String sql) {
			this.sql = sql;
		}
	}

	/** SQL の部品 */
	private static final class Where {
		static String key(String expr) {
			return "normalize(BTRIM(" + expr + "), NFKC)";
		}

		static final String LEAGUE = " WHERE season = :season"
				+ " AND " + key("country") + " = " + key("CAST(:country AS text)")
				+ " AND " + key("league") + " = " + key("CAST(:league AS text)");

		static String view(String view, String valueExpr, String countCol) {
			return "SELECT team, (" + valueExpr + ")::NUMERIC AS value, " + countCol + "::INTEGER AS match_count"
					+ " FROM " + view + LEAGUE + " AND ha = '*'";
		}
	}

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public UpcomingBadgeRepository(@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/** チームのシーズンの試合（ラウンド番号のあるものだけ。ラウンド → 試合日時の順） */
	public List<MatchRow> findMatches(String country, String league, String season, String team) {
		String sql = "SELECT round_no, ha, opponent, result, goals_for, goals_against, phase, first_goal,"
				+ " flow_known, ever_trailed, match_time"
				+ " FROM surface_overview_match" + Where.LEAGUE
				+ " AND " + Where.key("team") + " = " + Where.key("CAST(:team AS text)")
				+ " AND round_no IS NOT NULL"
				+ " ORDER BY round_no, match_time NULLS FIRST";
		return bmJdbcTemplate.query(sql, params(country, league, season).addValue("team", team), (rs, n) -> {
			MatchRow r = new MatchRow();
			r.setRoundNo(rs.getInt("round_no"));
			r.setHa(rs.getString("ha"));
			r.setOpponent(rs.getString("opponent"));
			r.setResult(rs.getString("result"));
			r.setGoalsFor(rs.getInt("goals_for"));
			r.setGoalsAgainst(rs.getInt("goals_against"));
			r.setPhase(rs.getString("phase"));
			r.setFirstGoal(rs.getString("first_goal"));
			r.setFlowKnown(rs.getBoolean("flow_known"));
			r.setEverTrailed(rs.getBoolean("ever_trailed"));
			r.setMatchTime(rs.getTimestamp("match_time"));
			return r;
		});
	}

	/** リーグ内の数値（値が無いチームは除く）。ビューが無い・失敗したときは空（アイコンを出さないだけ） */
	public List<MetricRow> findMetric(Metric metric, String country, String league, String season) {
		try {
			List<MetricRow> list = bmJdbcTemplate.query(metric.sql, params(country, league, season), (rs, n) -> {
				MetricRow r = new MetricRow();
				r.setTeam(rs.getString("team"));
				r.setValue(rs.getBigDecimal("value"));
				r.setMatchCount(rs.getInt("match_count"));
				return r;
			});
			List<MetricRow> out = new ArrayList<>();
			for (MetricRow r : list) {
				if (r.getTeam() != null && r.getValue() != null) {
					out.add(r);
				}
			}
			return out;
		} catch (Exception e) {
			log.warn("[UpcomingBadge] {} を取れませんでした e={}", metric, e.toString());
			return List.of();
		}
	}

	/** チームのスタイル（無ければ null） */
	public String findStyleLabel(String country, String league, String season, String team) {
		try {
			String sql = "SELECT style_label FROM team_style_profile" + Where.LEAGUE + " AND ha = '*'"
					+ " AND " + Where.key("team") + " = " + Where.key("CAST(:team AS text)") + " LIMIT 1";
			List<String> l = bmJdbcTemplate.queryForList(sql, params(country, league, season).addValue("team", team),
					String.class);
			return l.isEmpty() ? null : l.get(0);
		} catch (Exception e) {
			log.warn("[UpcomingBadge] スタイルを取れませんでした e={}", e.toString());
			return null;
		}
	}

	private static MapSqlParameterSource params(String country, String league, String season) {
		return new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league)
				.addValue("season", season);
	}
}
