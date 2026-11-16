package dev.web.repository.bm;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.util.DateOffsetDecisionUtil;
import dev.web.api.bm_a038.MatchDetailReferenceDTO;

/**
 * 統計詳細データ参照（BM_A038。2026-11 追加）
 * <p>B016 が登録した match_detail_match の一覧と、試合ごとの各テーブル（match_detail_*）の行数。soccer_bm。</p>
 * 試合ごとの中身（モメンタム・ゴール・メンバー・選手スタッツなど）は既存の
 * {@link MatchDetailExtraRepository}（GET /v1/api/match-detail/extra）で取る。
 * @author shiraishitoshio
 */
@Repository
public class MatchDetailReferenceRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public MatchDetailReferenceRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/** 検索条件 */
	private static final String WHERE = """
			WHERE (CAST(:keyword AS VARCHAR) IS NULL
			       OR m.match_id = CAST(:keyword AS VARCHAR)
			       OR normalize(COALESCE(m.category, ''), NFKC) ILIKE '%' || normalize(CAST(:keyword AS VARCHAR), NFKC) || '%'
			       OR normalize(COALESCE(m.home_team_name, ''), NFKC) ILIKE '%' || normalize(CAST(:keyword AS VARCHAR), NFKC) || '%'
			       OR normalize(COALESCE(m.away_team_name, ''), NFKC) ILIKE '%' || normalize(CAST(:keyword AS VARCHAR), NFKC) || '%')
			  AND (CAST(:dateFrom AS VARCHAR) IS NULL OR m.match_date >= CAST(CAST(:dateFrom AS VARCHAR) AS DATE))
			  AND (CAST(:dateTo AS VARCHAR) IS NULL OR m.match_date <= CAST(CAST(:dateTo AS VARCHAR) AS DATE))
			  AND (CAST(:errorsOnly AS BOOLEAN) = FALSE OR COALESCE(m.scrape_errors, '') <> '')
			""";

	private static MapSqlParameterSource params(String keyword, String dateFrom, String dateTo, boolean errorsOnly) {
		return new MapSqlParameterSource()
				.addValue("keyword", keyword)
				.addValue("dateFrom", dateFrom)
				.addValue("dateTo", dateTo)
				.addValue("errorsOnly", errorsOnly);
	}

	/** 件数 */
	public int count(String keyword, String dateFrom, String dateTo, boolean errorsOnly) {
		Integer n = bmJdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM match_detail_match m " + WHERE,
				params(keyword, dateFrom, dateTo, errorsOnly), Integer.class);
		return n == null ? 0 : n;
	}

	/** 一覧（試合日の新しい順）。各テーブルの行数つき */
	public List<MatchDetailReferenceDTO> find(String keyword, String dateFrom, String dateTo, boolean errorsOnly,
			int offset, int limit) {
		String sql = """
				SELECT m.match_id, to_char(m.match_date, 'YYYY-MM-DD') AS match_date, m.category,
				       m.home_team_name, m.away_team_name, m.home_score, m.away_score, m.status, m.start_time,
				       m.match_url, m.scraped_at, m.scrape_errors, m.register_time,
				       (SELECT COUNT(*) FROM match_detail_momentum x          WHERE x.match_id = m.match_id) AS momentum_count,
				       (SELECT COUNT(*) FROM match_detail_momentum_incident x WHERE x.match_id = m.match_id) AS incident_count,
				       (SELECT COUNT(*) FROM match_detail_summary x           WHERE x.match_id = m.match_id) AS summary_count,
				       (SELECT COUNT(*) FROM match_detail_goal x              WHERE x.match_id = m.match_id) AS goal_count,
				       (SELECT COUNT(*) FROM match_detail_formation x         WHERE x.match_id = m.match_id) AS formation_count,
				       (SELECT COUNT(*) FROM match_detail_lineup x            WHERE x.match_id = m.match_id) AS lineup_count,
				       (SELECT COUNT(*) FROM match_detail_missing x           WHERE x.match_id = m.match_id) AS missing_count,
				       (SELECT COUNT(*) FROM match_detail_member_stat x       WHERE x.match_id = m.match_id) AS member_stat_count
				FROM match_detail_match m
				""" + WHERE + """
				ORDER BY m.match_date DESC NULLS LAST, m.start_time DESC NULLS LAST, m.match_id
				OFFSET :offset LIMIT :limit
				""";
		MapSqlParameterSource p = params(keyword, dateFrom, dateTo, errorsOnly)
				.addValue("offset", offset)
				.addValue("limit", limit);
		return bmJdbcTemplate.query(sql, p, (rs, n) -> {
			MatchDetailReferenceDTO d = new MatchDetailReferenceDTO();
			d.setMatchId(rs.getString("match_id"));
			d.setMatchDate(rs.getString("match_date"));
			d.setCategory(rs.getString("category"));
			d.setHomeTeam(rs.getString("home_team_name"));
			d.setAwayTeam(rs.getString("away_team_name"));
			d.setHomeScore(rs.getString("home_score"));
			d.setAwayScore(rs.getString("away_score"));
			d.setStatus(rs.getString("status"));
			d.setStartTime(rs.getString("start_time"));
			d.setMatchUrl(rs.getString("match_url"));
			d.setScrapedAt(rs.getString("scraped_at"));
			d.setScrapeErrors(rs.getString("scrape_errors"));
			d.setRegisterTime(DateOffsetDecisionUtil.getJstLocalDateTimeString(rs, "register_time"));
			d.setMomentumCount(rs.getInt("momentum_count"));
			d.setIncidentCount(rs.getInt("incident_count"));
			d.setSummaryCount(rs.getInt("summary_count"));
			d.setGoalCount(rs.getInt("goal_count"));
			d.setFormationCount(rs.getInt("formation_count"));
			d.setLineupCount(rs.getInt("lineup_count"));
			d.setMissingCount(rs.getInt("missing_count"));
			d.setMemberStatCount(rs.getInt("member_stat_count"));
			return d;
		});
	}
}
