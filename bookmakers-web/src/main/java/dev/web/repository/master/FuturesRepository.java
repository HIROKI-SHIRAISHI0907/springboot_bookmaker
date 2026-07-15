package dev.web.repository.master;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w001.FuturesResponseDTO;
import lombok.Data;

/**
 * FuturesRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class FuturesRepository {

	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public FuturesRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	// ========================================================
	// 共通ヘルパー
	// ========================================================
	private Timestamp toStartOfDayJstTimestamp(String date) {
		LocalDate targetDate = LocalDate.parse(date.trim());
		ZonedDateTime zdt = targetDate.atStartOfDay(JST);
		return Timestamp.from(zdt.toInstant());
	}

	private Timestamp toNextStartOfDayJstTimestamp(String date) {
		LocalDate targetDate = LocalDate.parse(date.trim()).plusDays(1);
		ZonedDateTime zdt = targetDate.atStartOfDay(JST);
		return Timestamp.from(zdt.toInstant());
	}

	private String toIsoJstString(Timestamp ts) {
		if (ts == null) {
			return null;
		}
		return ts.toInstant().atZone(JST).toOffsetDateTime().toString();
	}

	private OffsetDateTime toOffsetDateTimeJst(Timestamp ts) {
		if (ts == null) {
			return null;
		}
		return ts.toInstant().atZone(JST).toOffsetDateTime();
	}

	// --------------------------------------------------------
	// 一覧: GET /api/future/:country/:league/:team（チーム取得）
	// --------------------------------------------------------
	public String findTeamJa(String country, String league, String teamSlug) {
		String sql = """
				SELECT team
				FROM country_league_master
				WHERE country = :country
				  AND league  = :league
				  AND link LIKE :link
				LIMIT 1
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league)
				.addValue("link", "/team/" + teamSlug + "/%");

		List<String> results = masterJdbcTemplate.query(
				sql,
				params,
				(rs, rowNum) -> rs.getString("team"));

		return results.isEmpty() ? teamSlug : results.get(0);
	}

	// --------------------------------------------------------
	// 取得: GET /api/future/{teamEnglish}/{teamHash}
	// --------------------------------------------------------
	public List<FuturesResponseDTO> findFutureMatches(String country, String league, String teamJa) {
		String likeCond = country + ": " + league + "%";

		String sql = """
				SELECT
				  f.seq,
				  f.game_team_category,
				  f.future_time,
				  f.home_team_name AS home_team,
				  f.away_team_name AS away_team,
				  NULLIF(TRIM(f.game_link), '') AS link,
				  CASE
				    WHEN regexp_match(f.game_team_category, '(ラウンド|Round)\\\\s*([0-9]+)') IS NULL THEN NULL
				    ELSE CAST((regexp_match(f.game_team_category, '(ラウンド|Round)\\\\s*([0-9]+)'))[2] AS INT)
				  END AS round_no
				FROM future_master f
				WHERE f.start_flg = '1'
				  AND f.future_time IS NOT NULL
				  AND f.future_time > CURRENT_TIMESTAMP
				  AND (f.home_team_name = :teamJa OR f.away_team_name = :teamJa)
				  AND f.game_team_category LIKE :likeCond
				ORDER BY
				  f.future_time ASC,
				  CASE
				    WHEN regexp_match(f.game_team_category, '(ラウンド|Round)\\\\s*([0-9]+)') IS NULL THEN 2147483647
				    ELSE CAST((regexp_match(f.game_team_category, '(ラウンド|Round)\\\\s*([0-9]+)'))[2] AS INT)
				  END ASC,
				  f.seq ASC
				LIMIT 1
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("teamJa", teamJa)
				.addValue("likeCond", likeCond);

		RowMapper<FuturesResponseDTO> rowMapper = (ResultSet rs, int rowNum) -> {
			FuturesResponseDTO m = new FuturesResponseDTO();

			m.setSeq(rs.getLong("seq"));
			m.setGameTeamCategory(rs.getString("game_team_category"));

			Timestamp ts = rs.getTimestamp("future_time");
			m.setFutureTime(toIsoJstString(ts));

			m.setHomeTeam(rs.getString("home_team"));
			m.setAwayTeam(rs.getString("away_team"));
			m.setLink(rs.getString("link"));

			int roundNo = rs.getInt("round_no");
			m.setRoundNo(rs.wasNull() ? null : roundNo);

			m.setStatus("SCHEDULED");
			return m;
		};

		return masterJdbcTemplate.query(sql, params, rowMapper);
	}

	public List<FutureMatchRow> findByIds(List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}

		String sql = """
				SELECT
				  seq AS id,
				  home_team_name,
				  away_team_name,
				  future_time
				FROM future_master
				WHERE seq IN (:ids)
				""";

		return masterJdbcTemplate.query(sql, Map.of("ids", ids), (rs, rowNum) -> {
			FutureMatchRow r = new FutureMatchRow();
			r.id = rs.getLong("id");
			r.homeTeamName = rs.getString("home_team_name");
			r.awayTeamName = rs.getString("away_team_name");

			Timestamp ts = rs.getTimestamp("future_time");
			r.matchStartTime = toOffsetDateTimeJst(ts);

			return r;
		});
	}

	/**
	 * 管理画面向け：次の日以降（JST基準）の試合候補を返す
	 * - country/league は任意（nullなら全件）
	 * - limit 件だけ返す
	 */
	public List<FuturesResponseDTO> findFutureMatchesFromNextDay(String country, String league, int limit) {
		// JSTで「明日の00:00」
		ZonedDateTime tomorrowStartJst = ZonedDateTime.now(JST)
				.plusDays(1)
				.toLocalDate()
				.atStartOfDay(JST);

		Timestamp from = Timestamp.from(tomorrowStartJst.toInstant());

		String likeCond = null;
		if (country != null && !country.isBlank() && league != null && !league.isBlank()) {
			likeCond = country + ": " + league + "%";
		} else if (country != null && !country.isBlank()) {
			likeCond = country + ":%";
		}

		String sql = """
				SELECT
				  f.seq,
				  f.game_team_category,
				  f.future_time,
				  f.home_team_name AS home_team,
				  f.away_team_name AS away_team,
				  NULLIF(TRIM(f.game_link), '') AS link,
				  CASE
				    WHEN regexp_match(f.game_team_category, '(ラウンド|Round)\\\\s*([0-9]+)') IS NULL THEN NULL
				    ELSE CAST((regexp_match(f.game_team_category, '(ラウンド|Round)\\\\s*([0-9]+)'))[2] AS INT)
				  END AS round_no
				FROM future_master f
				WHERE f.start_flg = '1'
				  AND f.future_time >= :from
				  AND (:likeCond IS NULL OR f.game_team_category LIKE :likeCond)
				ORDER BY f.future_time ASC
				LIMIT :limit
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("from", from)
				.addValue("likeCond", likeCond)
				.addValue("limit", limit);

		RowMapper<FuturesResponseDTO> rowMapper = (ResultSet rs, int rowNum) -> {
			FuturesResponseDTO m = new FuturesResponseDTO();

			m.setSeq(rs.getLong("seq"));
			m.setGameTeamCategory(rs.getString("game_team_category"));

			Timestamp ts = rs.getTimestamp("future_time");
			m.setFutureTime(toIsoJstString(ts));

			m.setHomeTeam(rs.getString("home_team"));
			m.setAwayTeam(rs.getString("away_team"));
			m.setLink(rs.getString("link"));

			int roundNo = rs.getInt("round_no");
			m.setRoundNo(rs.wasNull() ? null : roundNo);

			m.setStatus("SCHEDULED");
			return m;
		};

		return masterJdbcTemplate.query(sql, params, rowMapper);
	}

	// ========= future_master =========
	public List<FutureMasterIngestRow> findFutureMasterByRegisterTime(String country) {
	    StringBuilder sql = new StringBuilder("""
	            SELECT
	                seq,
	                game_team_category,
	                future_time,
	                home_team_name,
	                away_team_name,
	                game_link,
	                start_flg
	            FROM future_master
	            WHERE 1 = 1
	            """);

	    MapSqlParameterSource params = new MapSqlParameterSource();

	    if (country != null && !country.isBlank()) {
	        sql.append("""
	                AND game_team_category LIKE :countryLike
	                """);
	        params.addValue("countryLike", country.trim() + ":%");
	    }

	    sql.append("""
	            ORDER BY future_time ASC, seq ASC
	            """);

	    return masterJdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> {
	        FutureMasterIngestRow r = new FutureMasterIngestRow();
	        r.seq = rs.getLong("seq");
	        r.gameTeamCategory = rs.getString("game_team_category");

	        Timestamp ft = rs.getTimestamp("future_time");
	        r.futureTime = toIsoJstString(ft);

	        r.homeTeamName = rs.getString("home_team_name");
	        r.awayTeamName = rs.getString("away_team_name");
	        r.gameLink = rs.getString("game_link");
	        r.startFlg = rs.getString("start_flg");

	        return r;
	    });
	}

	/**
	 * 指定日の試合予定を JST基準で 10件ずつ OFFSET 取得
	 */
	public List<FuturesResponseDTO> findFutureMasterByDate(String date, int offset) {
		String sql = """
				SELECT
					seq,
					game_team_category,
					future_time,
					home_team_name AS home_team,
					away_team_name AS away_team,
					game_link AS link,
					start_flg
				FROM future_master
				WHERE future_time >= :dateStart
				  AND future_time < :dateEnd
				ORDER BY future_time ASC, seq ASC
				OFFSET :offset
				LIMIT 10
				""";

		if (date == null || date.isBlank()) {
			throw new IllegalArgumentException("date must not be blank");
		}
		if (offset < 0) {
			throw new IllegalArgumentException("offset must be greater than or equal to 0");
		}

		Timestamp dateStart = toStartOfDayJstTimestamp(date);
		Timestamp dateEnd = toNextStartOfDayJstTimestamp(date);

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("dateStart", dateStart)
				.addValue("dateEnd", dateEnd)
				.addValue("offset", offset);

		return masterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
			FuturesResponseDTO m = new FuturesResponseDTO();

			m.setSeq(rs.getLong("seq"));
			m.setGameTeamCategory(rs.getString("game_team_category"));

			Timestamp ts = rs.getTimestamp("future_time");
			m.setFutureTime(toIsoJstString(ts));

			m.setHomeTeam(rs.getString("home_team"));
			m.setAwayTeam(rs.getString("away_team"));
			m.setLink(rs.getString("link"));
			m.setStatus("SCHEDULED");

			return m;
		});
	}

	// row classes
	public static class FutureMasterIngestRow {
		public Long seq;
		public String gameTeamCategory;
		public String futureTime;
		public String homeTeamName;
		public String awayTeamName;
		public String gameLink;
		public String startFlg;
	}

	public static class FutureMatchRow {
		public Long id;
		public String homeTeamName;
		public String awayTeamName;
		public OffsetDateTime matchStartTime;
	}

	// ========= 各得点失点存在確認用 =========
	/**
	 * 各チームの試合候補（future_master）を返す
	 * - 対象: start_flg=1, future_time not null, チーム一致
	 * - リーグ: likeCond で絞る
	 * - 重複: (home, away, round_no) で DISTINCT
	 */
	public List<DataEachScoreLostDataResponseDTO> findEachScoreLoseMatchesExistsList(
			String country, String league, String teamJa) {

		String likeCond = country + ": " + league + "%";

		String sql = """
				WITH base AS (
				  SELECT
				    f.seq,
				    f.game_team_category AS data_category,
				    f.future_time        AS record_time,
				    f.home_team_name     AS home_team_name,
				    f.away_team_name     AS away_team_name,
				    NULLIF(BTRIM(f.game_link), '') AS link,
				    (regexp_match(f.game_link, 'mid=([A-Za-z0-9]+)'))[1] AS mid,
				    CASE
				      WHEN regexp_match(f.game_team_category, '(ラウンド|Round)\\\\s*([0-9]+)') IS NULL THEN NULL
				      ELSE CAST((regexp_match(f.game_team_category, '(ラウンド|Round)\\\\s*([0-9]+)'))[2] AS INT)
				    END AS round_no,
				    COALESCE(f.update_time, f.register_time, f.data_time, f.future_time) AS ut
				  FROM future_master f
				  WHERE f.start_flg = '1'
				    AND f.future_time IS NOT NULL
				    AND (f.home_team_name = :teamJa OR f.away_team_name = :teamJa)
				    AND f.game_team_category LIKE :likeCond
				    AND f.game_link IS NOT NULL
				    AND f.game_link ~ 'mid='
				),
				picked AS (
				  SELECT DISTINCT ON (mid)
				    seq, data_category, record_time, home_team_name, away_team_name, link, mid, round_no
				  FROM base
				  WHERE mid IS NOT NULL AND mid <> ''
				  ORDER BY mid, ut DESC, seq DESC
				)
				SELECT *
				FROM picked
				ORDER BY round_no DESC
				""";

		var params = new MapSqlParameterSource()
				.addValue("teamJa", teamJa)
				.addValue("likeCond", likeCond);

		RowMapper<DataEachScoreLostDataResponseDTO> rm = (ResultSet rs, int rowNum) -> {
			var dto = new DataEachScoreLostDataResponseDTO();
			dto.setSeq(rs.getLong("seq"));
			dto.setDataCategory(rs.getString("data_category"));

			int roundNo = rs.getInt("round_no");
			dto.setRoundNo(rs.wasNull() ? null : String.valueOf(roundNo));

			Timestamp rt = rs.getTimestamp("record_time");
			dto.setRecordTime(toIsoJstString(rt));

			dto.setHomeTeamName(rs.getString("home_team_name"));
			dto.setAwayTeamName(rs.getString("away_team_name"));
			dto.setLink(rs.getString("link"));
			return dto;
		};

		return masterJdbcTemplate.query(sql, params, rm);
	}

	@Data
	public class DataEachScoreLostDataResponseDTO {
		private Long seq;
		private String dataCategory;
		private String roundNo;
		private String recordTime;
		private String homeTeamName;
		private String awayTeamName;
		private Integer homeScore;
		private Integer awayScore;
		private String link;
	}

	// 過去の試合予定日用キックオフ時間取得
	public Map<String, String> findFutureTimeByGameLinks(String country, String league, List<String> links) {
		if (links == null || links.isEmpty()) {
			return Map.of();
		}

		String likeCond = country + ": " + league + "%";

		String sql = """
				SELECT DISTINCT ON (NULLIF(BTRIM(f.game_link), ''))
				  NULLIF(BTRIM(f.game_link), '') AS game_link,
				  f.future_time
				FROM future_master f
				WHERE f.start_flg = '1'
				  AND f.game_team_category LIKE :likeCond
				  AND NULLIF(BTRIM(f.game_link), '') IN (:links)
				ORDER BY NULLIF(BTRIM(f.game_link), ''), COALESCE(f.update_time, f.register_time) DESC, f.seq DESC
				""";

		var params = new MapSqlParameterSource()
				.addValue("likeCond", likeCond)
				.addValue("links", links);

		return masterJdbcTemplate.query(sql, params, (ResultSet rs) -> {
			java.util.Map<String, String> out = new java.util.HashMap<>();
			while (rs.next()) {
				String link = rs.getString("game_link");
				Timestamp ft = rs.getTimestamp("future_time");
				if (link == null || link.isBlank() || ft == null) {
					continue;
				}
				out.put(link, toIsoJstString(ft));
			}
			return out;
		});
	}

	// “matchKey” を game_link から抽出
	public java.util.Set<String> findExistingMatchKeys(List<String> keys) {
		if (keys == null || keys.isEmpty()) {
			return java.util.Set.of();
		}

		String sql = """
				WITH base AS (
				  SELECT
				    COALESCE(
				      NULLIF((regexp_match(f.game_link, 'mid=([A-Za-z0-9]+)'))[1], ''),
				      NULLIF(BTRIM(f.game_link), '')
				    ) AS match_key
				  FROM future_master f
				  WHERE f.game_link IS NOT NULL
				)
				SELECT DISTINCT match_key
				FROM base
				WHERE match_key IN (:keys)
				""";

		var params = new MapSqlParameterSource().addValue("keys", keys);
		List<String> xs = masterJdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("match_key"));
		return new java.util.HashSet<>(xs);
	}

	// 「matchKey(mid)→game_link」取得メソッド
	public Map<String, String> findGameLinksByMatchKeys(List<String> keys) {
		if (keys == null || keys.isEmpty()) {
			return Map.of();
		}

		String sql = """
				WITH base AS (
				  SELECT
				    (regexp_match(f.game_link, 'mid=([A-Za-z0-9]+)'))[1] AS mid,
				    NULLIF(BTRIM(f.game_link), '') AS game_link,
				    COALESCE(f.update_time, f.register_time) AS ut,
				    f.seq AS seq
				  FROM future_master f
				  WHERE f.game_link IS NOT NULL
				    AND f.game_link ~ 'mid='
				),
				picked AS (
				  SELECT DISTINCT ON (mid)
				    mid, game_link
				  FROM base
				  WHERE mid IN (:keys)
				  ORDER BY mid, ut DESC, seq DESC
				)
				SELECT mid, game_link
				FROM picked
				""";

		var params = new MapSqlParameterSource().addValue("keys", keys);

		List<Map.Entry<String, String>> rows = masterJdbcTemplate.query(sql, params,
				(rs, rowNum) -> Map.entry(rs.getString("mid"), rs.getString("game_link")));

		Map<String, String> out = new java.util.HashMap<>();
		for (var e : rows) {
			if (e.getKey() != null && e.getValue() != null) {
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}
}
