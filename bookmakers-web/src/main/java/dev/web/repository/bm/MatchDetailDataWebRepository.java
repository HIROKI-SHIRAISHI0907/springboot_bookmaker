package dev.web.repository.bm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 統計詳細データ（B016 が aws-s3-detail-data-csv から登録する match_detail_* テーブル）参照用リポジトリ
 * @author shiraishitoshio
 */
@Repository
public class MatchDetailDataWebRepository {

	/** IN 句 1 回あたりの件数 */
	private static final int IN_CHUNK_SIZE = 1000;

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public MatchDetailDataWebRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/**
	 * 指定した試合ID（Flashscore の mid）のうち、match_detail_match に登録済みのものを返す。
	 *
	 * @param matchIds 調べる試合ID
	 * @return 登録済みの試合ID
	 */
	public Set<String> findRegisteredMatchIds(Collection<String> matchIds) {
		Set<String> result = new HashSet<>();
		if (matchIds == null || matchIds.isEmpty()) {
			return result;
		}

		List<String> ids = new ArrayList<>(new HashSet<>(matchIds));
		String sql = """
				    SELECT DISTINCT match_id
				    FROM match_detail_match
				    WHERE match_id IN (:ids)
				""";

		for (int i = 0; i < ids.size(); i += IN_CHUNK_SIZE) {
			List<String> chunk = ids.subList(i, Math.min(i + IN_CHUNK_SIZE, ids.size()));
			result.addAll(bmJdbcTemplate.queryForList(
					sql, new MapSqlParameterSource().addValue("ids", chunk), String.class));
		}
		return result;
	}

	/**
	 * 統計詳細データの 9 テーブル（キー → テーブル名）。画面の表示順
	 */
	public static final Map<String, String> DETAIL_TABLES;
	static {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("match", "match_detail_match");
		m.put("summary", "match_detail_summary");
		m.put("goal", "match_detail_goal");
		m.put("momentum", "match_detail_momentum");
		m.put("momentumIncident", "match_detail_momentum_incident");
		m.put("formation", "match_detail_formation");
		m.put("lineup", "match_detail_lineup");
		m.put("missing", "match_detail_missing");
		m.put("memberStat", "match_detail_member_stat");
		DETAIL_TABLES = java.util.Collections.unmodifiableMap(m);
	}

	/**
	 * 指定した試合ID（mid）ごとに、統計詳細データの 9 テーブルのうちデータ（1 行以上）があるテーブルのキーを返す。
	 * データが 1 つも無い試合は結果に入らない。
	 * （2026-11-18 追加: 画面で「9 データのうちいくつ取れているか」を出すため）
	 *
	 * @param matchIds 調べる試合ID
	 * @return 試合ID → データがあるテーブルのキー（DETAIL_TABLES のキー）
	 */
	public Map<String, Set<String>> findDetailTablesByMatchIds(Collection<String> matchIds) {
		Map<String, Set<String>> result = new HashMap<>();
		if (matchIds == null || matchIds.isEmpty()) {
			return result;
		}

		StringBuilder sql = new StringBuilder();
		for (Map.Entry<String, String> e : DETAIL_TABLES.entrySet()) {
			if (sql.length() > 0) {
				sql.append(" UNION ALL ");
			}
			sql.append("SELECT DISTINCT match_id, '").append(e.getKey()).append("' AS tbl FROM ")
					.append(e.getValue()).append(" WHERE match_id IN (:ids)");
		}

		List<String> ids = new ArrayList<>(new HashSet<>(matchIds));
		for (int i = 0; i < ids.size(); i += IN_CHUNK_SIZE) {
			List<String> chunk = ids.subList(i, Math.min(i + IN_CHUNK_SIZE, ids.size()));
			bmJdbcTemplate.query(sql.toString(), new MapSqlParameterSource().addValue("ids", chunk), rs -> {
				result.computeIfAbsent(rs.getString("match_id"), k -> new HashSet<>()).add(rs.getString("tbl"));
			});
		}
		return result;
	}
}
