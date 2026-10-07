package dev.web.repository.bm;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.dashboard.matchDTO.DashboardMatchRow;
import dev.web.api.dashboard.teamDTO.DashboardTeamRateRow;


/**
 * トップ画面（Dashboard）用 Repository（soccer_bm: static_data・ビュー dashboard_team_rate）
 *
 * <ul>
 *   <li>{@link #findLatestSince}: 指定時刻以降に記録がある試合の、試合ごとの最新行（ライブ・今日終了した試合の元データ）。
 *       試合は match_id（空ならホーム・アウェー）で区別する。前半のスコアはハーフタイムの行から取る。</li>
 *   <li>{@link #findTeamRates}: 今季のチーム × H/A の平均得点・失点とリーグ平均。</li>
 * </ul>
 * ※ static_data.record_time は JST の日時（timestamp）。試合内の並びは seq_key 末尾の数字（"0drkxQrA-12" → 12）の数値順。
 *
 * @author shiraishitoshio
 */
@Repository
public class DashboardMatchRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public DashboardMatchRepository(@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/**
	 * 試合ごとの最新行（since 以降に記録がある試合）
	 *
	 * @param since この時刻（JST）以降に記録がある試合
	 */
	public List<DashboardMatchRow> findLatestSince(LocalDateTime since) {
		String sql = """
				WITH recent AS (
				  SELECT d.*,
				         COALESCE(NULLIF(BTRIM(d.match_id), ''), d.home_team_name || '|' || d.away_team_name) AS match_key,
				         NULLIF(SUBSTRING(d.seq_key FROM '([0-9]+)$'), '')::BIGINT AS seq_no
				  FROM static_data d
				  WHERE d.record_time >= :since
				    AND d.home_team_name IS NOT NULL AND d.away_team_name IS NOT NULL
				),
				latest AS (
				  SELECT DISTINCT ON (match_key) *
				  FROM recent
				  ORDER BY match_key, seq_no DESC NULLS LAST, record_time DESC
				),
				halftime AS (
				  -- 前半終了時のスコア（ハーフタイムの行。試合ごとに最新の1行）
				  SELECT DISTINCT ON (match_key) match_key, home_score, away_score
				  FROM recent
				  WHERE BTRIM(times) = 'ハーフタイム'
				  ORDER BY match_key, seq_no DESC NULLS LAST
				)
				SELECT
				  l.seq_key,
				  l.match_id,
				  l.data_category,
				  BTRIM(l.times)    AS times,
				  l.home_team_name,
				  l.away_team_name,
				  CAST(l.home_rank AS text)     AS home_rank,
				  CAST(l.away_rank AS text)     AS away_rank,
				  CAST(l.home_score AS text)    AS home_score,
				  CAST(l.away_score AS text)    AS away_score,
				  CAST(l.home_exp AS text)      AS home_exp,
				  CAST(l.away_exp AS text)      AS away_exp,
				  CAST(l.home_shoot_in AS text) AS home_shoot_in,
				  CAST(l.away_shoot_in AS text) AS away_shoot_in,
				  CAST(l.home_donation AS text) AS home_donation,
				  CAST(l.away_donation AS text) AS away_donation,
				  CAST(l.record_time AS text)   AS record_time,
				  CAST(ht.home_score AS text)   AS ht_home_score,
				  CAST(ht.away_score AS text)   AS ht_away_score
				FROM latest l
				LEFT JOIN halftime ht ON ht.match_key = l.match_key
				ORDER BY l.data_category, l.seq_key
				""";
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("since", Timestamp.valueOf(since));
		return bmJdbcTemplate.query(sql, params, (rs, n) -> {
			DashboardMatchRow r = new DashboardMatchRow();
			r.setSeqKey(rs.getString("seq_key"));
			r.setMatchId(rs.getString("match_id"));
			r.setDataCategory(rs.getString("data_category"));
			r.setTimes(rs.getString("times"));
			r.setHomeTeamName(rs.getString("home_team_name"));
			r.setAwayTeamName(rs.getString("away_team_name"));
			r.setHomeRank(rs.getString("home_rank"));
			r.setAwayRank(rs.getString("away_rank"));
			r.setHomeScore(rs.getString("home_score"));
			r.setAwayScore(rs.getString("away_score"));
			r.setHomeExp(rs.getString("home_exp"));
			r.setAwayExp(rs.getString("away_exp"));
			r.setHomeShootIn(rs.getString("home_shoot_in"));
			r.setAwayShootIn(rs.getString("away_shoot_in"));
			r.setHomeDonation(rs.getString("home_donation"));
			r.setAwayDonation(rs.getString("away_donation"));
			r.setRecordTime(rs.getString("record_time"));
			r.setHtHomeScore(rs.getString("ht_home_score"));
			r.setHtAwayScore(rs.getString("ht_away_score"));
			return r;
		});
	}

	/**
	 * 今季のチーム × H/A の平均得点・失点（全リーグ）
	 */
	public List<DashboardTeamRateRow> findTeamRates() {
		String sql = """
				SELECT
				  country, league, season, team, ha, match_count,
				  avg_goals_for, avg_goals_against, league_avg_goals_for, league_avg_goals_against
				FROM dashboard_team_rate
				""";
		return bmJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			DashboardTeamRateRow r = new DashboardTeamRateRow();
			r.setCountry(rs.getString("country"));
			r.setLeague(rs.getString("league"));
			r.setSeason(rs.getString("season"));
			r.setTeam(rs.getString("team"));
			r.setHa(rs.getString("ha"));
			r.setMatchCount(rs.getInt("match_count"));
			r.setAvgGoalsFor(rs.getDouble("avg_goals_for"));
			r.setAvgGoalsAgainst(rs.getDouble("avg_goals_against"));
			r.setLeagueAvgGoalsFor(rs.getDouble("league_avg_goals_for"));
			r.setLeagueAvgGoalsAgainst(rs.getDouble("league_avg_goals_against"));
			return r;
		});
	}
}
