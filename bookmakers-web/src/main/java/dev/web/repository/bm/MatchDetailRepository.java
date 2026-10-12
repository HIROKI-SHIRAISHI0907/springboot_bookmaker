package dev.web.repository.bm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 試合詳細（MatchDetail.tsx）用 Repository（soccer_bm: static_data）
 * @author shiraishitoshio
 */
@Repository
public class MatchDetailRepository {

	/** 取得する列（全て text で受け取る） */
	public static final String[] COLUMNS = {
			"seq_key", "match_id", "data_category", "times",
			"home_rank", "away_rank", "home_team_name", "away_team_name", "home_score", "away_score",
			"home_exp", "away_exp", "home_in_goal_exp", "away_in_goal_exp",
			"home_donation", "away_donation", "home_shoot_all", "away_shoot_all",
			"home_shoot_in", "away_shoot_in", "home_big_chance", "away_big_chance",
			"home_corner", "away_corner", "home_pass_count", "away_pass_count",
			"home_foul", "away_foul", "home_offside", "away_offside",
			"home_yellow_card", "away_yellow_card", "home_red_card", "away_red_card",
			"home_keeper_save", "away_keeper_save",
			"record_time", "time_sort_seconds",
			"weather", "temparature", "humid", "judge_member",
			"home_manager", "away_manager", "home_formation", "away_formation",
			"studium", "capacity", "audience", "location",
			"goal_time", "goal_team_member" };

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public MatchDetailRepository(@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/**
	 * seq_key の試合の全ての行（同じ match_id、または seq_key が同じ "接頭辞-N" の行）。
	 * 並び順: 試合時間（秒）→ 記録時刻 → 連番。退避中の行（"~" 始まり）は除く。
	 */
	public List<Map<String, String>> findMatchRows(String seqKey) {
		StringBuilder cols = new StringBuilder();
		for (String c : COLUMNS) {
			if (cols.length() > 0) {
				cols.append(", ");
			}
			cols.append("NULLIF(BTRIM(CAST(d.").append(c).append(" AS text)), '') AS ").append(c);
		}
		String sql = """
				WITH base AS (
				    SELECT NULLIF(BTRIM(match_id), '')              AS mid,
				           regexp_replace(seq_key, '-[0-9]+$', '')  AS prefix
				    FROM static_data
				    WHERE seq_key = :seqKey
				    LIMIT 1
				)
				SELECT %s
				FROM static_data d
				CROSS JOIN base b
				WHERE (d.match_id = b.mid OR d.seq_key LIKE b.prefix || '-%%')
				  AND d.seq_key NOT LIKE '~%%'
				ORDER BY
				    NULLIF(regexp_replace(CAST(d.time_sort_seconds AS text), '[^0-9.]', '', 'g'), '')::numeric ASC NULLS FIRST,
				    d.record_time ASC NULLS FIRST,
				    NULLIF(SUBSTRING(d.seq_key FROM '-([0-9]+)$'), '')::bigint ASC NULLS LAST
				""".formatted(cols);
		List<Map<String, Object>> raw = bmJdbcTemplate.queryForList(sql, new MapSqlParameterSource("seqKey", seqKey));
		List<Map<String, String>> out = new ArrayList<>(raw.size());
		for (Map<String, Object> r : raw) {
			Map<String, String> m = new LinkedHashMap<>();
			for (String c : COLUMNS) {
				Object v = r.get(c);
				m.put(c, v == null ? null : v.toString());
			}
			out.add(m);
		}
		return out;
	}
}
