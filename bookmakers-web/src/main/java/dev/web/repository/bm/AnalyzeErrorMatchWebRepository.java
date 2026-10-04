package dev.web.repository.bm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a099.AnalyzeErrorDTO;
import dev.web.api.bm_a099.AnalyzeErrorSearchCondition;
import dev.web.api.bm_a099.AnalyzeErrorSummaryDTO;

/**
 * AnalyzeErrorMatchWebRepositoryクラス（analyze_error_match: 統計処理で登録できなかった試合）
 *
 * <ul>
 *   <li>一覧: 条件は入力があるものだけ WHERE に足す（NULL を渡して「:x IS NULL OR …」とすると PostgreSQL で型が決まらずエラーになるため）。</li>
 *   <li>一覧ではスタックトレース（最大 4000 文字）を返さない。詳細（findBySeq）だけで返す。</li>
 *   <li>対応済みの更新は画面からの手動。バッチ側は同じエラーが再発すると未対応（resolved_flg = FALSE）に戻す。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Repository
public class AnalyzeErrorMatchWebRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public AnalyzeErrorMatchWebRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/** 一覧の列（スタックトレース以外） */
	private static final String LIST_COLUMNS = """
			      seq,
			      bm_number,
			      error_type,
			      error_message,
			      country,
			      league,
			      data_category,
			      home_team_name,
			      away_team_name,
			      error_field,
			      error_value,
			      match_id,
			      season,
			      detail,
			      exception_class,
			      occurred_count,
			      first_occurred_at,
			      last_occurred_at,
			      resolved_flg,
			      resolved_at,
			      resolved_by,
			      note
			""";

	// --------------------------------------------------------
	// 一覧: GET /api/analyze-error/matches
	// --------------------------------------------------------
	public List<AnalyzeErrorDTO> search(AnalyzeErrorSearchCondition cond, int offset, int limit) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String where = buildWhere(cond, params);
		params.addValue("offset", offset);
		params.addValue("limit", limit);

		String sql = "SELECT" + LIST_COLUMNS
				+ " FROM analyze_error_match"
				+ where
				+ " ORDER BY last_occurred_at DESC, seq DESC"
				+ " OFFSET :offset LIMIT :limit";

		return bmJdbcTemplate.query(sql, params, (rs, n) -> toDto(rs, false));
	}

	// --------------------------------------------------------
	// 件数: GET /api/analyze-error/matches（total）
	// --------------------------------------------------------
	public int count(AnalyzeErrorSearchCondition cond) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String sql = "SELECT COUNT(*) FROM analyze_error_match" + buildWhere(cond, params);
		Integer total = bmJdbcTemplate.queryForObject(sql, params, Integer.class);
		return total == null ? 0 : total;
	}

	// --------------------------------------------------------
	// 詳細: GET /api/analyze-error/matches/{seq}
	// --------------------------------------------------------
	public AnalyzeErrorDTO findBySeq(String seq) {
		String sql = "SELECT" + LIST_COLUMNS + ", stack_trace"
				+ " FROM analyze_error_match WHERE seq = :seq";

		List<AnalyzeErrorDTO> list = bmJdbcTemplate.query(
				sql, new MapSqlParameterSource().addValue("seq", seq), (rs, n) -> toDto(rs, true));
		return list.isEmpty() ? null : list.get(0);
	}

	// --------------------------------------------------------
	// ダウンロード: GET /api/analyze-error/matches/export
	//   一覧と同じ条件で、スタックトレースも含めて最大 max 件
	// --------------------------------------------------------
	public List<AnalyzeErrorDTO> searchForExport(AnalyzeErrorSearchCondition cond, int max) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String where = buildWhere(cond, params);
		params.addValue("limit", max);

		String sql = "SELECT" + LIST_COLUMNS + ", stack_trace"
				+ " FROM analyze_error_match"
				+ where
				+ " ORDER BY last_occurred_at DESC, seq DESC"
				+ " LIMIT :limit";

		return bmJdbcTemplate.query(sql, params, (rs, n) -> toDto(rs, true));
	}

	// --------------------------------------------------------
	// 集計: GET /api/analyze-error/summary
	// --------------------------------------------------------
	public List<AnalyzeErrorSummaryDTO> findSummary() {
		String sql = """
				    SELECT
				      bm_number,
				      error_type,
				      country,
				      league,
				      error_field,
				      match_count,
				      unresolved_count,
				      occurred_total,
				      first_occurred_at,
				      last_occurred_at,
				      latest_message
				    FROM analyze_error_summary
				    ORDER BY unresolved_count DESC, last_occurred_at DESC
				""";

		return bmJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			AnalyzeErrorSummaryDTO dto = new AnalyzeErrorSummaryDTO();
			dto.setBmNumber(rs.getString("bm_number"));
			dto.setErrorType(rs.getString("error_type"));
			dto.setCountry(rs.getString("country"));
			dto.setLeague(rs.getString("league"));
			dto.setErrorField(rs.getString("error_field"));
			dto.setMatchCount(rs.getInt("match_count"));
			dto.setUnresolvedCount(rs.getInt("unresolved_count"));
			dto.setOccurredTotal(rs.getInt("occurred_total"));
			dto.setFirstOccurredAt(toIso(rs.getTimestamp("first_occurred_at")));
			dto.setLastOccurredAt(toIso(rs.getTimestamp("last_occurred_at")));
			dto.setLatestMessage(rs.getString("latest_message"));
			return dto;
		});
	}

	// --------------------------------------------------------
	// 対応状況・メモの更新: PATCH /api/analyze-error/matches/{seq}
	//   resolvedFlg が null ならメモだけ更新する。note が null ならメモは変えない。
	// --------------------------------------------------------
	public int updateResolved(String seq, Boolean resolvedFlg, String resolvedBy, String note) {
		StringBuilder sql = new StringBuilder("UPDATE analyze_error_match SET ");
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("seq", seq)
				.addValue("resolved_by", resolvedBy);

		if (resolvedFlg != null) {
			if (resolvedFlg) {
				sql.append("resolved_flg = TRUE, resolved_at = CURRENT_TIMESTAMP, resolved_by = :resolved_by, ");
			} else {
				sql.append("resolved_flg = FALSE, resolved_at = NULL, resolved_by = NULL, ");
			}
		}
		if (note != null) {
			sql.append("note = :note, ");
			params.addValue("note", note);
		}
		sql.append("update_id = :resolved_by, update_time = CURRENT_TIMESTAMP WHERE seq = :seq");

		return bmJdbcTemplate.update(sql.toString(), params);
	}

	/**
	 * 一覧の検索条件（入力がある条件だけ足す）。
	 */
	private static String buildWhere(AnalyzeErrorSearchCondition cond, MapSqlParameterSource params) {
		StringBuilder where = new StringBuilder(" WHERE 1 = 1");
		if (cond == null) {
			return where.toString();
		}
		if ("unresolved".equals(cond.getStatus())) {
			where.append(" AND resolved_flg = FALSE");
		} else if ("resolved".equals(cond.getStatus())) {
			where.append(" AND resolved_flg = TRUE");
		}
		if (notBlank(cond.getBmNumber())) {
			where.append(" AND bm_number = :bm_number");
			params.addValue("bm_number", cond.getBmNumber().trim());
		}
		if (notBlank(cond.getErrorType())) {
			where.append(" AND error_type = :error_type");
			params.addValue("error_type", cond.getErrorType().trim());
		}
		if (notBlank(cond.getCountry())) {
			where.append(" AND country = :country");
			params.addValue("country", cond.getCountry().trim());
		}
		if (notBlank(cond.getLeague())) {
			where.append(" AND league = :league");
			params.addValue("league", cond.getLeague().trim());
		}
		if (notBlank(cond.getErrorField())) {
			// error_field は「homeScore」「country,league」のようにカンマ区切りなので、項目名の一致で探す
			where.append(" AND :error_field = ANY (string_to_array(error_field, ','))");
			params.addValue("error_field", cond.getErrorField().trim());
		}
		if (cond.getSeqs() != null && !cond.getSeqs().isEmpty()) {
			where.append(" AND seq IN (:seqs)");
			params.addValue("seqs", cond.getSeqs());
		}
		if (notBlank(cond.getKeyword())) {
			where.append(" AND (home_team_name ILIKE :keyword OR away_team_name ILIKE :keyword"
					+ " OR data_category ILIKE :keyword OR error_message ILIKE :keyword"
					+ " OR match_id ILIKE :keyword OR error_value ILIKE :keyword)");
			params.addValue("keyword", "%" + escapeLike(cond.getKeyword().trim()) + "%");
		}
		return where.toString();
	}

	private static AnalyzeErrorDTO toDto(ResultSet rs, boolean withStackTrace) throws SQLException {
		AnalyzeErrorDTO dto = new AnalyzeErrorDTO();
		dto.setSeq(rs.getString("seq"));
		dto.setBmNumber(rs.getString("bm_number"));
		dto.setErrorType(rs.getString("error_type"));
		dto.setErrorMessage(rs.getString("error_message"));
		dto.setCountry(rs.getString("country"));
		dto.setLeague(rs.getString("league"));
		dto.setDataCategory(rs.getString("data_category"));
		dto.setHomeTeamName(rs.getString("home_team_name"));
		dto.setAwayTeamName(rs.getString("away_team_name"));
		dto.setErrorField(rs.getString("error_field"));
		dto.setErrorValue(rs.getString("error_value"));
		dto.setMatchId(rs.getString("match_id"));
		dto.setSeason(rs.getString("season"));
		dto.setDetail(rs.getString("detail"));
		dto.setExceptionClass(rs.getString("exception_class"));
		dto.setOccurredCount(rs.getInt("occurred_count"));
		dto.setFirstOccurredAt(toIso(rs.getTimestamp("first_occurred_at")));
		dto.setLastOccurredAt(toIso(rs.getTimestamp("last_occurred_at")));
		dto.setResolvedFlg(rs.getBoolean("resolved_flg"));
		dto.setResolvedAt(toIso(rs.getTimestamp("resolved_at")));
		dto.setResolvedBy(rs.getString("resolved_by"));
		dto.setNote(rs.getString("note"));
		if (withStackTrace) {
			dto.setStackTrace(rs.getString("stack_trace"));
		}
		return dto;
	}

	/** 日時を ISO-8601（UTC。例: 2026-10-03T01:23:45Z）に。画面で JST に変換する */
	private static String toIso(Timestamp ts) {
		return ts == null ? null : ts.toInstant().toString();
	}

	/** ILIKE の % _ \ をそのままの文字として探す */
	private static String escapeLike(String s) {
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}
}
