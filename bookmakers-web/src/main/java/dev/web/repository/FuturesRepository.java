package dev.web.repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w001.FuturesResponseDTO;

/**
 * FuturesRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class FuturesRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public FuturesRepository(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    // --------------------------------------------------------
    // 一覧: GET /api/:country/:league/:team/future（チーム取得）
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

        List<String> results = namedJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> rs.getString("team")
        );

        return results.isEmpty() ? teamSlug : results.get(0);
    }

    // --------------------------------------------------------
    // 取得: GET /api/:country/:league/:team/future
    // --------------------------------------------------------
    public List<FuturesResponseDTO> findFutureMatches(String teamJa, String country, String league) {
        String likeCond = country + ": " + league + "%";

        String sql = """
            SELECT
              (f.seq)::text AS seq,
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

        return namedJdbcTemplate.query(sql, params, rowMapper);
    }
}