package dev.web.repository.bm;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.team.bm_w001.TeamResultRow;


/**
 * チームの過去の結果（soccer_bm: surface_overview_match・static_data）
 *
 * <ul>
 *   <li>{@link #findLatestSeason}: 国・リーグの最新シーズン（surface_overview_match）</li>
 *   <li>{@link #findSeasonStart}: そのシーズンの最初の試合時刻（static_data を今季だけに絞る用）</li>
 *   <li>{@link #findOverviewResults}: surface_overview_match のチームの試合（ラウンドの新しい順）</li>
 *   <li>{@link #findStaticFinished}: static_data の「終了済」の行（ラウンドごとに最新の1行。スコアが空なら同じ試合の最後のスコア）</li>
 * </ul>
 * 国・リーグ・チーム名は NFKC 正規化して比べる。
 * @author shiraishitoshio
 */
@Repository
public class TeamResultsRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public TeamResultsRepository(@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/** 国・リーグの最新シーズン（無ければ null） */
	public String findLatestSeason(String country, String league) {
		String sql = """
				SELECT MAX(season)
				FROM surface_overview_match
				WHERE normalize(country, NFKC) = normalize(:country, NFKC)
				  AND normalize(league, NFKC)  = normalize(:league, NFKC)
				""";
		return bmJdbcTemplate.queryForObject(sql, params(country, league, null), String.class);
	}

	/** そのシーズンの最初の試合時刻（JST。無ければ null） */
	public LocalDateTime findSeasonStart(String country, String league, String season) {
		String sql = """
				SELECT MIN(match_time) AT TIME ZONE 'Asia/Tokyo'
				FROM surface_overview_match
				WHERE normalize(country, NFKC) = normalize(:country, NFKC)
				  AND normalize(league, NFKC)  = normalize(:league, NFKC)
				  AND season = :season
				""";
		Timestamp ts = bmJdbcTemplate.queryForObject(sql, params(country, league, null).addValue("season", season),
				Timestamp.class);
		return ts == null ? null : ts.toLocalDateTime();
	}

	/** surface_overview_match のチームの試合（そのシーズン・ラウンドの新しい順） */
	public List<TeamResultRow> findOverviewResults(String country, String league, String team, String season,
			int maxRows) {
		String sql = """
				SELECT round_no, opponent, ha, goals_for, goals_against, result, pk_flg,
				       pk_goals_for, pk_goals_against,
				       CAST(match_time AT TIME ZONE 'Asia/Tokyo' AS text) AS match_time
				FROM surface_overview_match
				WHERE normalize(country, NFKC) = normalize(:country, NFKC)
				  AND normalize(league, NFKC)  = normalize(:league, NFKC)
				  AND normalize(team, NFKC)    = normalize(:team, NFKC)
				  AND season = :season
				ORDER BY round_no DESC
				LIMIT :maxRows
				""";
		MapSqlParameterSource p = params(country, league, team).addValue("season", season).addValue("maxRows", maxRows);
		return bmJdbcTemplate.query(sql, p, (rs, n) -> {
			TeamResultRow r = new TeamResultRow();
			r.setSource("OVERVIEW");
			r.setRoundNo(rs.getInt("round_no"));
			r.setOpponent(rs.getString("opponent"));
			r.setHa(rs.getString("ha"));
			r.setGoalsFor((Integer) rs.getObject("goals_for", Integer.class));
			r.setGoalsAgainst((Integer) rs.getObject("goals_against", Integer.class));
			r.setResult(rs.getString("result"));
			r.setPk(rs.getBoolean("pk_flg"));
			r.setPkGoalsFor((Integer) rs.getObject("pk_goals_for", Integer.class));
			r.setPkGoalsAgainst((Integer) rs.getObject("pk_goals_against", Integer.class));
			r.setMatchTime(rs.getString("match_time"));
			return r;
		});
	}

	/**
	 * static_data の「終了済」の行（ラウンドごとに最新の1行）。
	 * 終了済の行のスコアが空のときは、同じ試合（match_id）でスコアが入っている最後の行のスコアを使う。
	 *
	 * @param from この時刻（JST）以降の記録だけ（去年の同じラウンドを混ぜないため）
	 */
	public List<TeamResultRow> findStaticFinished(String country, String league, String team, LocalDateTime from) {
		String sql = """
				SELECT DISTINCT ON (x.round_no)
				       x.round_no, x.home_team_name, x.away_team_name,
				       COALESCE(CASE WHEN x.has_score THEN BTRIM(x.home_score) END, sc.home_score) AS home_score,
				       COALESCE(CASE WHEN x.has_score THEN BTRIM(x.away_score) END, sc.away_score) AS away_score,
				       CAST(x.record_time AS text) AS record_time
				FROM (
				  SELECT d.*,
				         NULLIF(SUBSTRING(d.data_category FROM 'ラウンド\\s*([0-9]+)'), '')::INTEGER AS round_no,
				         (BTRIM(d.home_score) ~ '^[0-9]+$' AND BTRIM(d.away_score) ~ '^[0-9]+$') AS has_score
				  FROM static_data d
				  WHERE BTRIM(d.times) = '終了済'
				    AND d.record_time >= :from
				    AND normalize(BTRIM(split_part(d.data_category, ':', 1)), NFKC) = normalize(:country, NFKC)
				    AND normalize(BTRIM(split_part(split_part(d.data_category, ':', 2), ' - ', 1)), NFKC)
				        = normalize(:league, NFKC)
				    AND (normalize(d.home_team_name, NFKC) = normalize(:team, NFKC)
				         OR normalize(d.away_team_name, NFKC) = normalize(:team, NFKC))
				) x
				LEFT JOIN LATERAL (
				  SELECT BTRIM(s.home_score) AS home_score, BTRIM(s.away_score) AS away_score
				  FROM static_data s
				  WHERE NULLIF(BTRIM(x.match_id), '') IS NOT NULL
				    AND s.match_id = x.match_id
				    AND BTRIM(s.home_score) ~ '^[0-9]+$' AND BTRIM(s.away_score) ~ '^[0-9]+$'
				  ORDER BY NULLIF(SUBSTRING(s.seq_key FROM '([0-9]+)$'), '')::BIGINT DESC NULLS LAST, s.record_time DESC
				  LIMIT 1
				) sc ON TRUE
				WHERE x.round_no IS NOT NULL
				ORDER BY x.round_no, x.record_time DESC NULLS LAST
				""";
		MapSqlParameterSource p = params(country, league, team).addValue("from", Timestamp.valueOf(from));
		return bmJdbcTemplate.query(sql, p, (rs, n) -> {
			TeamResultRow r = new TeamResultRow();
			r.setSource("STATIC");
			r.setRoundNo(rs.getInt("round_no"));
			r.setHomeTeamName(rs.getString("home_team_name"));
			r.setAwayTeamName(rs.getString("away_team_name"));
			r.setHomeScore(rs.getString("home_score"));
			r.setAwayScore(rs.getString("away_score"));
			r.setMatchTime(rs.getString("record_time"));
			return r;
		});
	}

	private static MapSqlParameterSource params(String country, String league, String team) {
		return new MapSqlParameterSource()
				.addValue("country", country == null ? "" : country)
				.addValue("league", league == null ? "" : league)
				.addValue("team", team == null ? "" : team);
	}
}
