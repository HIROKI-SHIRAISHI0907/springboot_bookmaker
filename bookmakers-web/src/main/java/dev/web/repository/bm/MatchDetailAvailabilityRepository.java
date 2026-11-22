package dev.web.repository.bm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 統計詳細データの「リーグごとの取れ具合」（2026-11-22 追加。soccer_bm）
 * <p>リーグ = match_detail_match.category の「 - 」より前（例: 「サッカー / 日本 / J2 リーグ」）。
 * リーグごとに直近 recent 試合のうち、各テーブル（match_detail_*）にデータ（1 行以上）がある試合の数を返す。
 * Flashscore がそのリーグでは出していないデータ（例: J2 のモメンタム）を「提供なし」と判断するのに使う。</p>
 * @author shiraishitoshio
 */
@Repository
public class MatchDetailAvailabilityRepository {

	/** 試合数を表すキー（テーブルのキーとぶつからない名前） */
	public static final String TOTAL = "_total";

	private static final int IN_CHUNK_SIZE = 1000;

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public MatchDetailAvailabilityRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/** リーグのキー（category の「 - 」より前） */
	public static String leagueOf(String category) {
		if (category == null) {
			return "";
		}
		int i = category.indexOf(" - ");
		return (i >= 0 ? category.substring(0, i) : category).trim();
	}

	/**
	 * 試合ID → リーグ（match_detail_match にある試合だけ）
	 */
	public Map<String, String> findLeagueByMatchIds(Collection<String> matchIds) {
		Map<String, String> out = new HashMap<>();
		if (matchIds == null || matchIds.isEmpty()) {
			return out;
		}
		List<String> ids = new ArrayList<>(new LinkedHashSet<>(matchIds));
		for (int i = 0; i < ids.size(); i += IN_CHUNK_SIZE) {
			List<String> chunk = ids.subList(i, Math.min(i + IN_CHUNK_SIZE, ids.size()));
			bmJdbcTemplate.query("SELECT match_id, category FROM match_detail_match WHERE match_id IN (:ids)",
					new MapSqlParameterSource("ids", chunk),
					rs -> {
						out.put(rs.getString("match_id"), leagueOf(rs.getString("category")));
					});
		}
		return out;
	}

	/**
	 * 試合ID → 試合日（yyyy-MM-dd。match_detail_match にある試合だけ。2026-11-22 追加: 取り直しの期間を過ぎたかの判定用）
	 */
	public Map<String, String> findMatchDateByMatchIds(Collection<String> matchIds) {
		Map<String, String> out = new HashMap<>();
		if (matchIds == null || matchIds.isEmpty()) {
			return out;
		}
		List<String> ids = new ArrayList<>(new LinkedHashSet<>(matchIds));
		for (int i = 0; i < ids.size(); i += IN_CHUNK_SIZE) {
			List<String> chunk = ids.subList(i, Math.min(i + IN_CHUNK_SIZE, ids.size()));
			bmJdbcTemplate.query(
					"SELECT match_id, to_char(match_date, 'YYYY-MM-DD') AS d FROM match_detail_match WHERE match_id IN (:ids)",
					new MapSqlParameterSource("ids", chunk),
					rs -> {
						if (rs.getString("d") != null) {
							out.put(rs.getString("match_id"), rs.getString("d"));
						}
					});
		}
		return out;
	}

	/**
	 * リーグ → （テーブルのキー → データがある試合の数）。{@link #TOTAL} にリーグの試合数（直近 recent 試合まで）。
	 *
	 * @param leagues リーグ（{@link #leagueOf}）
	 * @param tables  テーブルのキー → テーブル名（{@link MatchDetailDataWebRepository#DETAIL_TABLES}）
	 * @param recent  リーグごとに見る直近の試合数
	 */
	public Map<String, Map<String, Integer>> findLeagueTableCounts(Collection<String> leagues,
			Map<String, String> tables, int recent) {
		Map<String, Map<String, Integer>> out = new HashMap<>();
		if (leagues == null || leagues.isEmpty()) {
			return out;
		}
		StringBuilder sql = new StringBuilder("""
				WITH x AS (
				  SELECT match_id, league,
				         ROW_NUMBER() OVER (PARTITION BY league ORDER BY match_date DESC NULLS LAST, scraped_at DESC NULLS LAST) AS rn
				  FROM (
				    SELECT match_id, match_date, scraped_at,
				           CASE WHEN strpos(COALESCE(category, ''), ' - ') > 0
				                THEN btrim(substr(category, 1, strpos(category, ' - ') - 1))
				                ELSE btrim(COALESCE(category, '')) END AS league
				    FROM match_detail_match
				  ) y
				),
				m AS (
				  SELECT match_id, league FROM x WHERE league IN (:leagues) AND rn <= :recent
				)
				""");
		sql.append("SELECT league, '").append(TOTAL).append("' AS tbl, COUNT(*) AS n FROM m GROUP BY league");
		for (Map.Entry<String, String> e : tables.entrySet()) {
			sql.append(" UNION ALL SELECT m.league, '").append(e.getKey())
					.append("' AS tbl, COUNT(*) AS n FROM m WHERE EXISTS (SELECT 1 FROM ").append(e.getValue())
					.append(" t WHERE t.match_id = m.match_id) GROUP BY m.league");
		}
		bmJdbcTemplate.query(sql.toString(), new MapSqlParameterSource()
				.addValue("leagues", new ArrayList<>(new LinkedHashSet<>(leagues)))
				.addValue("recent", recent), rs -> {
					out.computeIfAbsent(rs.getString("league"), k -> new HashMap<>())
							.put(rs.getString("tbl"), rs.getInt("n"));
				});
		return out;
	}
}
