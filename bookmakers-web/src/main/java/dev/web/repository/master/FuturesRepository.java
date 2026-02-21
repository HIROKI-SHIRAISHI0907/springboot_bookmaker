package dev.web.repository.master;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public FuturesRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
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
				    WHEN regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN NULL
				    ELSE CAST( (regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)'))[2] AS INT )
				  END AS round_no
				FROM future_master f
				WHERE f.start_flg = '1'
				  AND (f.home_team_name = :teamJa OR f.away_team_name = :teamJa)
				  AND f.game_team_category LIKE :likeCond
				ORDER BY
				  CASE
				    WHEN regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN 2147483647
				    ELSE CAST( (regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)'))[2] AS INT )
				  END ASC,
				  f.future_time ASC
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("teamJa", teamJa)
				.addValue("likeCond", likeCond);

		RowMapper<FuturesResponseDTO> rowMapper = (ResultSet rs, int rowNum) -> {
			FuturesResponseDTO m = new FuturesResponseDTO();

			m.setSeq(Long.parseLong(rs.getString("seq")));
			m.setGameTeamCategory(rs.getString("game_team_category"));

			Timestamp ts = rs.getTimestamp("future_time");
			if (ts != null) {
				OffsetDateTime odt = ts.toInstant().atOffset(ZoneOffset.UTC);
				m.setFutureTime(odt.toString());
			} else {
				m.setFutureTime(null);
			}

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
		if (ids == null || ids.isEmpty())
			return List.of();

		String sql = """
				    SELECT
				      seq,
				      home_team_name,
				      away_team_name,
				      future_time
				    FROM future_master
				    WHERE id IN (:ids)
				""";

		return masterJdbcTemplate.query(sql, Map.of("ids", ids), (rs, rowNum) -> {
			FutureMatchRow r = new FutureMatchRow();
			r.id = rs.getLong("seq");
			r.homeTeamName = rs.getString("home_team_name");
			r.awayTeamName = rs.getString("away_team_name");
			Timestamp ts = rs.getTimestamp("future_time");
			if (ts != null) {
				r.matchStartTime = ts.toInstant().atOffset(ZoneOffset.UTC);
			} else {
				r.matchStartTime = null;
			}
			return r;
		});
	}

	/**
	 * 管理画面向け：次の日以降（JST 기준）の試合候補を返す
	 * - country/league は任意（nullなら全件）
	 * - limit 件だけ返す
	 */
	public List<FuturesResponseDTO> findFutureMatchesFromNextDay(String country, String league, int limit) {
		// JSTで「明日の00:00」
		ZonedDateTime tomorrowStartJst = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"))
				.plusDays(1)
				.toLocalDate()
				.atStartOfDay(ZoneId.of("Asia/Tokyo"));

		OffsetDateTime from = tomorrowStartJst.toOffsetDateTime();

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
				        WHEN regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN NULL
				        ELSE CAST( (regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)'))[2] AS INT )
				      END AS round_no
				    FROM future_master f
				    WHERE f.start_flg = '1'
				      AND f.future_time >= :from
				      AND (:likeCond IS NULL OR f.game_team_category LIKE :likeCond)
				    ORDER BY
				      f.future_time ASC
				    LIMIT :limit
				""";

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("from", from)
				.addValue("likeCond", likeCond)
				.addValue("limit", limit);

		RowMapper<FuturesResponseDTO> rowMapper = (ResultSet rs, int rowNum) -> {
			FuturesResponseDTO m = new FuturesResponseDTO();

			// ★管理画面で必要：future_master.id を DTO に入れる
			m.setSeq(Long.parseLong(rs.getString("seq")));
			m.setGameTeamCategory(rs.getString("game_team_category"));

			Timestamp ts = rs.getTimestamp("future_time");
			if (ts != null) {
				OffsetDateTime odt = ts.toInstant().atOffset(ZoneOffset.UTC);
				m.setFutureTime(odt.toString());
			} else {
				m.setFutureTime(null);
			}

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
	public List<FutureMasterIngestRow> findFutureMasterByRegisterTime(OffsetDateTime from, OffsetDateTime to) {
		String sql = """
				    SELECT
				      seq,
				      game_team_category,
				      future_time,
				      home_team_name,
				      away_team_name,
				      game_link,
				      start_flg,
				      register_time,
				      update_time
				    FROM future_master
				    WHERE register_time >= :from
				      AND register_time <  :to
				    ORDER BY register_time DESC
				""";

		var params = new MapSqlParameterSource()
				.addValue("from", from)
				.addValue("to", to);

		return masterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
			FutureMasterIngestRow r = new FutureMasterIngestRow();
			r.seq = rs.getLong("seq");
			r.gameTeamCategory = rs.getString("game_team_category");

			Timestamp ft = rs.getTimestamp("future_time");
			r.futureTime = (ft == null) ? null : ft.toInstant().atOffset(ZoneOffset.UTC).toString();

			r.homeTeamName = rs.getString("home_team_name");
			r.awayTeamName = rs.getString("away_team_name");
			r.gameLink = rs.getString("game_link");
			r.startFlg = rs.getString("start_flg");

			Timestamp rt = rs.getTimestamp("register_time");
			r.registerTime = (rt == null) ? null : rt.toInstant().atOffset(ZoneOffset.UTC);

			Timestamp ut = rs.getTimestamp("update_time");
			r.updateTime = (ut == null) ? null : ut.toInstant().atOffset(ZoneOffset.UTC);

			return r;
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
		public OffsetDateTime registerTime;
		public OffsetDateTime updateTime;
	}

	public static class FutureMatchRow {
		public Long id;
		public String homeTeamName;
		public String awayTeamName;
		public java.time.OffsetDateTime matchStartTime;
	}

	// ========= 各得点失点存在確認用 =========
	// FuturesRepository.java

	public List<DataEachScoreLostDataResponseDTO> findEachScoreLoseMatchesExistsList(
	        String country, String league, String teamJa
	) {
	    String likeCond = country + ": " + league + "%";

	    String sql = """
	        SELECT
	          f.seq,
	          f.game_team_category AS data_category,
	          f.future_time        AS record_time,
	          f.home_team_name     AS home_team_name,
	          f.away_team_name     AS away_team_name,
	          NULLIF(TRIM(f.game_link), '') AS link,
	          CASE
	            WHEN regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN NULL
	            ELSE CAST((regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)'))[2] AS INT)
	          END AS round_no
	        FROM future_master f
	        WHERE f.start_flg = '1'
	          AND (f.home_team_name = :teamJa OR f.away_team_name = :teamJa)
	          AND f.game_team_category LIKE :likeCond
	        ORDER BY
	          CASE
	            WHEN regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN 2147483647
	            ELSE CAST((regexp_match(f.game_team_category, '(ラウンド|Round)\\s*([0-9]+)'))[2] AS INT)
	          END ASC,
	          f.future_time ASC
	    """;

	    var params = new MapSqlParameterSource()
	            .addValue("teamJa", teamJa)
	            .addValue("likeCond", likeCond);

	    RowMapper<DataEachScoreLostDataResponseDTO> rm = (rs, rowNum) -> {
	        var dto = new DataEachScoreLostDataResponseDTO();
	        dto.setSeq(rs.getLong("seq"));
	        dto.setDataCategory(rs.getString("data_category"));

	        int roundNo = rs.getInt("round_no");
	        dto.setRoundNo(rs.wasNull() ? null : String.valueOf(roundNo)); // DTOがStringならこれ
	        // dto.setRoundNo(rs.wasNull() ? null : roundNo);              // DTOをInteger化できるならこっち推奨

	        Timestamp rt = rs.getTimestamp("record_time");
	        dto.setRecordTime(rt == null ? null : rt.toInstant().atOffset(ZoneOffset.UTC).toString());

	        dto.setHomeTeamName(rs.getString("home_team_name"));
	        dto.setAwayTeamName(rs.getString("away_team_name"));

	        dto.setHomeScore(null);
	        dto.setAwayScore(null);

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
}