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

import dev.web.api.bm_u003.FavoriteScope;

/**
 * FavoriteRepository
 * @author shiraishitoshio
 *
 */
@Repository
public class FavoriteRepository {

    private final @Qualifier("userJdbcTemplate")
    	NamedParameterJdbcTemplate userJdbcTemplate;

    public FavoriteRepository(
            @Qualifier("webUserJdbcTemplate") NamedParameterJdbcTemplate userJdbcTemplate
    ) {
        this.userJdbcTemplate = userJdbcTemplate;
    }

    // -----------------------------
    // 登録（1本化）
    // level: 1=country, 2=league, 3=team
    // league/team は NULL ではなく "" を入れる（Prisma/制約に合わせる）
    // -----------------------------
    public int insert(Long userId, int level, String country, String league, String team, String operatorId) {

        String sql = """
            INSERT INTO favorites(
              user_id, level, country, league, team,
              register_id, register_time, update_id, update_time
            )
            VALUES (
              :userId, :level, :country, :league, :team,
              :operatorId, now(), :operatorId, now()
            )
            ON CONFLICT (user_id, level, country, league, team) DO NOTHING
            """;

        Map<String, Object> params = Map.of(
                "userId", userId,
                "level", level,
                "country", country,
                "league", league,
                "team", team,
                "operatorId", operatorId
        );

        return userJdbcTemplate.update(sql, params);
    }

    // -----------------------------
    // 削除（親削除→子削除はDBトリガで実現想定）
    // -----------------------------
    public int deleteById(Long userId, Long id) {
        String sql = "DELETE FROM favorites WHERE user_id = :userId AND id = :id";
        return userJdbcTemplate.update(sql, Map.of("userId", userId, "id", id));
    }

    // -----------------------------
    // 取得（フィルタ用 Scope）
    // “強い設定があるなら弱い設定は返さない”ルールに対応
    // -----------------------------
    public FavoriteScope findFavoriteScope(Long userId) {

        // 0) 空なら全表示
        String countSql = "SELECT COUNT(*) FROM favorites WHERE user_id = :userId";
        Long cnt = userJdbcTemplate.queryForObject(countSql, Map.of("userId", userId), Long.class);

        if (cnt == null || cnt == 0L) {
            FavoriteScope s = new FavoriteScope();
            s.setAllowAll(true);
            s.setAllowedCountries(List.of());
            s.setAllowedLeaguesByCountry(Map.of());
            s.setAllowedTeamsByCountryLeague(Map.of());
            return s;
        }

        // 1) 国のみ（国に league/team があれば除外）
        String countriesSql = """
            SELECT DISTINCT f.country
            FROM favorites f
            WHERE f.user_id = :userId
              AND f.level = 1
              AND NOT EXISTS (
                SELECT 1 FROM favorites x
                WHERE x.user_id = f.user_id
                  AND x.country = f.country
                  AND x.level IN (2, 3)
              )
            ORDER BY f.country
            """;

        Set<String> allowedCountries = new LinkedHashSet<>(userJdbcTemplate.query(
                countriesSql,
                Map.of("userId", userId),
                (rs, n) -> rs.getString("country")
        ));

        // 2) 国リーグのみ（同一country+leagueに team があれば除外）
        String leaguesSql = """
            SELECT DISTINCT f.country, f.league
            FROM favorites f
            WHERE f.user_id = :userId
              AND f.level = 2
              AND NOT EXISTS (
                SELECT 1 FROM favorites x
                WHERE x.user_id = f.user_id
                  AND x.country = f.country
                  AND x.league  = f.league
                  AND x.level = 3
              )
            ORDER BY f.country, f.league
            """;

        Map<String, Set<String>> leaguesByCountrySet = new LinkedHashMap<>();
        userJdbcTemplate.query(leaguesSql, Map.of("userId", userId), rs -> {
            String country = rs.getString("country");
            String league = rs.getString("league");
            leaguesByCountrySet
                    .computeIfAbsent(country, k -> new LinkedHashSet<>())
                    .add(league);
        });

        // 3) 国リーグチーム（team登録）
        String teamsSql = """
            SELECT DISTINCT f.country, f.league, f.team
            FROM favorites f
            WHERE f.user_id = :userId
              AND f.level = 3
            ORDER BY f.country, f.league, f.team
            """;

        Map<String, Set<String>> teamsByCountryLeagueSet = new LinkedHashMap<>();
        userJdbcTemplate.query(teamsSql, Map.of("userId", userId), rs -> {
            String country = rs.getString("country");
            String league = rs.getString("league");
            String team = rs.getString("team");
            String key = country + "|" + league;
            teamsByCountryLeagueSet
                    .computeIfAbsent(key, k -> new LinkedHashSet<>())
                    .add(team);
        });

        // Set -> List
        Map<String, List<String>> leaguesByCountry = new LinkedHashMap<>();
        leaguesByCountrySet.forEach((k, v) -> leaguesByCountry.put(k, new ArrayList<>(v)));

        Map<String, List<String>> teamsByCountryLeague = new LinkedHashMap<>();
        teamsByCountryLeagueSet.forEach((k, v) -> teamsByCountryLeague.put(k, new ArrayList<>(v)));

        FavoriteScope scope = new FavoriteScope();
        scope.setAllowAll(false);
        scope.setAllowedCountries(new ArrayList<>(allowedCountries));
        scope.setAllowedLeaguesByCountry(leaguesByCountry);
        scope.setAllowedTeamsByCountryLeague(teamsByCountryLeague);
        return scope;
    }
}
