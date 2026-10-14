package dev.web.repository.bm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.Data;

/**
 * 試合予定（future_master）ごとの static_data の登録状況を取得する（指定日の試合予定一覧用）
 *
 * <p>「同じ試合」の判定（B010 / B012 と同じ考え方）</p>
 * <ul>
 *   <li>game_link の mid と static_data.match_id が同じ、または</li>
 *   <li>同じカード（NFKC 正規化）で record_time が試合開始日時の 1時間前〜6時間後</li>
 * </ul>
 * record_time / future_time はどちらも timestamp（日本時間の壁時計）として扱う。
 * @author shiraishitoshio
 *
 */
@Repository
public class FutureMatchDataStatusRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public FutureMatchDataStatusRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/**
	 * 試合ごとの集計を返す（キーは future_master.seq）。
	 * 1ページ分（最大100件）を VALUES で渡して1回の SQL で集計する。
	 */
	public Map<Long, MatchStatusRow> findStatus(List<MatchKey> keys) {
		Map<Long, MatchStatusRow> out = new HashMap<>();
		if (keys == null || keys.isEmpty()) {
			return out;
		}

		StringBuilder values = new StringBuilder();
		MapSqlParameterSource params = new MapSqlParameterSource();
		for (int i = 0; i < keys.size(); i++) {
			MatchKey k = keys.get(i);
			if (i > 0) {
				values.append(",\n");
			}
			values.append("(CAST(:seq").append(i).append(" AS bigint), ")
					.append("CAST(:home").append(i).append(" AS text), ")
					.append("CAST(:away").append(i).append(" AS text), ")
					.append("CAST(:mid").append(i).append(" AS text), ")
					.append("CAST(:ft").append(i).append(" AS timestamp))");
			params.addValue("seq" + i, k.getSeq());
			params.addValue("home" + i, k.getHomeTeamName());
			params.addValue("away" + i, k.getAwayTeamName());
			params.addValue("mid" + i, blankToNull(k.getMatchId()));
			params.addValue("ft" + i, blankToNull(k.getFutureTime()));
		}

		String sql = """
				WITH m(seq, home, away, mid, ft) AS (
				  VALUES
				  %s
				)
				SELECT
				  m.seq,
				  COUNT(d.record_time) FILTER (WHERE d.fin)                  AS finished_count,
				  COUNT(d.record_time) FILTER (WHERE NOT d.fin)              AS live_count,
				  COUNT(d.record_time) FILTER (
				    WHERE d.record_time >= (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Tokyo') - INTERVAL '40 minutes'
				  )                                                          AS current_live_count,
				  to_char(MAX(d.record_time), 'YYYY-MM-DD"T"HH24:MI:SS')     AS last_updated_at
				FROM m
				LEFT JOIN LATERAL (
				  SELECT
				    s.record_time,
				    REPLACE(BTRIM(normalize(COALESCE(s.times, ''), NFKC)), ' ', '') = '終了済' AS fin
				  FROM static_data s
				  WHERE (m.mid IS NOT NULL AND s.match_id = m.mid)
				     OR (
				          normalize(s.home_team_name, NFKC) = normalize(m.home, NFKC)
				      AND normalize(s.away_team_name, NFKC) = normalize(m.away, NFKC)
				      AND m.ft IS NOT NULL
				      AND s.record_time >= m.ft - INTERVAL '1 hour'
				      AND s.record_time <  m.ft + INTERVAL '6 hours'
				     )
				) d ON TRUE
				GROUP BY m.seq
				""".formatted(values);

		List<MatchStatusRow> rows = bmJdbcTemplate.query(sql, params, (rs, rowNum) -> {
			MatchStatusRow r = new MatchStatusRow();
			r.setSeq(rs.getLong("seq"));
			r.setFinishedCount(rs.getInt("finished_count"));
			r.setLiveCount(rs.getInt("live_count"));
			r.setCurrentLiveCount(rs.getInt("current_live_count"));
			String last = rs.getString("last_updated_at");
			r.setLastUpdatedAt(last == null ? null : last + "+09:00");
			return r;
		});
		for (MatchStatusRow r : rows) {
			out.put(r.getSeq(), r);
		}
		return out;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	/** 集計対象の試合（future_master 1行分） */
	@Data
	public static class MatchKey {
		private Long seq;
		private String homeTeamName;
		private String awayTeamName;
		/** game_link の mid（無ければ null） */
		private String matchId;
		/** 試合開始日時（"yyyy-MM-dd HH:mm:ss"・日本時間。無ければ null） */
		private String futureTime;
	}

	/** 試合ごとの集計結果 */
	@Data
	public static class MatchStatusRow {
		private Long seq;
		private int finishedCount;
		private int liveCount;
		private int currentLiveCount;
		/** 最終記録日時（ISO・+09:00） */
		private String lastUpdatedAt;
	}
}
