package dev.web.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * CorrelationsRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
@RequiredArgsConstructor
public class CorrelationsRepository {

	@Qualifier("bmJdbcTemplate")
    private final NamedParameterJdbcTemplate bmJdbcTemplate;

	@Qualifier("masterJdbcTemplate")
    private final NamedParameterJdbcTemplate masterJdbcTemplate;

    // --------------------------------------------------------
    // 一覧: GET /api/:country/:league/:team/correlations（チーム取得）
    // --------------------------------------------------------
    public String findTeamName(String country, String league, String teamSlug) {
        String sql = """
            SELECT clm.team
            FROM country_league_master clm
            WHERE clm.country = :country
              AND clm.league  = :league
              AND clm.link LIKE :slug
            LIMIT 1
        """;

        List<String> list = masterJdbcTemplate.query(
            sql,
            new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("slug", "/team/" + teamSlug + "/%"),
            (rs, n) -> rs.getString("team")
        );

        return list.isEmpty() ? null : list.get(0);
    }

    // --------------------------------------------------------
    // 一覧: GET /api/:country/:league/:team/correlations（相関係数取得）
    // --------------------------------------------------------
    public List<Map<String, Object>> findCorrelationRows(
            String country, String league, String teamName, String opponentFilter) {

        String sql = """
            SELECT
              r.*,
              CASE WHEN r.home = :team THEN 'home'
                   WHEN r.away = :team THEN 'away'
              END AS side,
              CASE WHEN r.home = :team THEN r.away ELSE r.home END AS opponent
            FROM calc_correlation_ranking r
            WHERE r.country = :country
              AND r.league  = :league
              AND (r.home = :team OR r.away = :team)
              AND r.score IN ('1st','2nd','ALL')
        """;

        if (opponentFilter != null && !opponentFilter.isBlank()) {
            sql += " AND (CASE WHEN r.home = :team THEN r.away ELSE r.home END) = :opponent ";
        }

        return bmJdbcTemplate.query(
            sql,
            new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("team", teamName)
                .addValue("opponent", opponentFilter),
            (rs, n) -> {
                Map<String, Object> map = new HashMap<>();
                var rsmd = rs.getMetaData();
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    map.put(rsmd.getColumnLabel(i), rs.getObject(i));
                }
                return map;
            }
        );
    }
}
