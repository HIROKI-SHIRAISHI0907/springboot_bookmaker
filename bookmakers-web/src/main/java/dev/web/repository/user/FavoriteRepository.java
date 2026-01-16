package dev.web.repository.user;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_u003.FavoriteScopeResponse;

/**
 * FavoriteRepository
 * @author shiraishitoshio
 *
 */
@Repository
public class FavoriteRepository {

    private final NamedParameterJdbcTemplate userJdbcTemplate;

    public FavoriteRepository(
            @Qualifier("webUserJdbcTemplate") NamedParameterJdbcTemplate userJdbcTemplate
    ) {
        this.userJdbcTemplate = userJdbcTemplate;
    }

    // =============================
    // フィルタ用：許可条件の返却
    // =============================
    public FavoriteScopeResponse findFavoriteScope(Integer userId) {

        // 0) まず件数チェック（空なら全表示）
        String countSql = "SELECT COUNT(*) FROM favorites WHERE user_id = :userId";
        Long cnt = userJdbcTemplate.queryForObject(
        		countSql, Map.of("userId", userId), Long.class);
        if (cnt == null || cnt == 0L) {
        	FavoriteScopeResponse s = new FavoriteScopeResponse();
            s.setAllowAll(true);
            s.setAllowedCountries(List.of());
            s.setAllowedLeaguesByCountry(Map.of());
            s.setAllowedTeamsByCountryLeague(Map.of());
            return s;
        }

        // --- 以降、前回のロジック（国→リーグ→チーム、強い方があれば弱い方は除外） ---
        // 1) 国（level=1）
        String countrySql = """
            SELECT DISTINCT country
            FROM favorites
            WHERE user_id = :userId
              AND level = 1
            ORDER BY country
            """;

        Set<String> allowedCountries = new LinkedHashSet<>(userJdbcTemplate.query(
                countrySql,
                Map.of("userId", userId),
                (rs, n) -> rs.getString("country")
        ));

        // 2) 国リーグ（level=2）※国がある国は除外
        String leagueSql = """
            SELECT DISTINCT country, league
            FROM favorites f
            WHERE f.user_id = :userId
              AND f.level = 2
              AND NOT EXISTS (
                SELECT 1 FROM favorites c
                WHERE c.user_id = f.user_id
                  AND c.level = 1
                  AND c.country = f.country
              )
            ORDER BY country, league
            """;

        Map<String, Set<String>> leaguesByCountrySet = new LinkedHashMap<>();
        userJdbcTemplate.query(leagueSql, Map.of("userId", userId), rs -> {
            leaguesByCountrySet
                    .computeIfAbsent(rs.getString("country"), k -> new LinkedHashSet<>())
                    .add(rs.getString("league"));
        });

        // 3) 国リーグチーム（level=3）※国・国リーグがある場合は除外
        String teamSql = """
            SELECT DISTINCT country, league, team
            FROM favorites f
            WHERE f.user_id = :userId
              AND f.level = 3
              AND NOT EXISTS (
                SELECT 1 FROM favorites c
                WHERE c.user_id = f.user_id
                  AND c.level = 1
                  AND c.country = f.country
              )
              AND NOT EXISTS (
                SELECT 1 FROM favorites l
                WHERE l.user_id = f.user_id
                  AND l.level = 2
                  AND l.country = f.country
                  AND l.league = f.league
              )
            ORDER BY country, league, team
            """;

        Map<String, Set<String>> teamsByCountryLeagueSet = new LinkedHashMap<>();
        userJdbcTemplate.query(teamSql, Map.of("userId", userId), rs -> {
            String key = rs.getString("country") + "|" + rs.getString("league");
            teamsByCountryLeagueSet
                    .computeIfAbsent(key, k -> new LinkedHashSet<>())
                    .add(rs.getString("team"));
        });

        // Set -> List
        Map<String, List<String>> leaguesByCountry = new LinkedHashMap<>();
        leaguesByCountrySet.forEach((k, v) -> leaguesByCountry.put(k, new ArrayList<>(v)));

        Map<String, List<String>> teamsByCountryLeague = new LinkedHashMap<>();
        teamsByCountryLeagueSet.forEach((k, v) -> teamsByCountryLeague.put(k, new ArrayList<>(v)));

        FavoriteScopeResponse scope = new FavoriteScopeResponse();
        scope.setAllowAll(false);
        scope.setAllowedCountries(new ArrayList<>(allowedCountries));
        scope.setAllowedLeaguesByCountry(leaguesByCountry);
        scope.setAllowedTeamsByCountryLeague(teamsByCountryLeague);
        return scope;
    }


}
