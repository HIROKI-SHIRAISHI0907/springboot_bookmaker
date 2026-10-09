package dev.web.repository.user;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_u003.FavoriteItem;
import dev.web.api.bm_u003.FavoriteScope;
import dev.web.api.favorite.bm_w001.FavoriteTeamDTO;

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

	/**
	 * チームのお気に入りを、親（国 level 1・リーグ level 2）と一緒に登録する（1文・既にあれば何もしない）
	 * favorites_parent_check トリガーが親の存在を確認するため、国 → リーグ → チームの順に入れる。
	 * @return チーム（level 3）を新しく登録した件数（既にあれば 0）
	 */
	public int insertTeamWithParents(Long userId, String country, String league, String team, String operatorId) {
		String sql = """
			INSERT INTO favorites (
			  user_id, "level", country, league, team,
			  register_id, register_time, update_id, update_time
			)
			VALUES
			  (:userId, CAST(1 AS smallint), :country, '',      '',    :operatorId, CURRENT_TIMESTAMP, :operatorId, CURRENT_TIMESTAMP),
			  (:userId, CAST(2 AS smallint), :country, :league, '',    :operatorId, CURRENT_TIMESTAMP, :operatorId, CURRENT_TIMESTAMP),
			  (:userId, CAST(3 AS smallint), :country, :league, :team, :operatorId, CURRENT_TIMESTAMP, :operatorId, CURRENT_TIMESTAMP)
			ON CONFLICT (user_id, "level", country, league, team) DO NOTHING
			RETURNING "level"
			""";
		MapSqlParameterSource p = new MapSqlParameterSource()
				.addValue("userId", userId)
				.addValue("country", country)
				.addValue("league", league)
				.addValue("team", team)
				.addValue("operatorId", operatorId);
		List<Integer> inserted = userJdbcTemplate.queryForList(sql, p, Integer.class);
		return (int) inserted.stream().filter(l -> l != null && l == 3).count();
	}

	/**
	 * お気に入りを登録（同じものがあれば何もしない）
	 * @return 登録件数（既にあれば 0）
	 */
	public int insert(Long userId, int level, String country, String league, String team, String operatorId) {
		String sql = """
			INSERT INTO favorites (
			  user_id, "level", country, league, team,
			  register_id, register_time, update_id, update_time
			)
			VALUES (
			  :userId, CAST(:level AS smallint), :country, :league, :team,
			  :operatorId, CURRENT_TIMESTAMP, :operatorId, CURRENT_TIMESTAMP
			)
			ON CONFLICT (user_id, "level", country, league, team) DO NOTHING
			""";
		MapSqlParameterSource p = new MapSqlParameterSource()
				.addValue("userId", userId)
				.addValue("level", level)
				.addValue("country", country == null ? "" : country)
				.addValue("league", league == null ? "" : league)
				.addValue("team", team == null ? "" : team)
				.addValue("operatorId", operatorId);
		return userJdbcTemplate.update(sql, p);
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
              AND f."level" = '1'
              AND NOT EXISTS (
                SELECT 1
                FROM favorites x
                WHERE x.user_id = f.user_id
                  AND x.country = f.country
                  AND x."level" IN ('2', '3')
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
              AND f."level" = '2'
              AND NOT EXISTS (
                SELECT 1
                FROM favorites x
                WHERE x.user_id = f.user_id
                  AND x.country = f.country
                  AND x.league  = f.league
                  AND x."level" = '3'
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
              AND f."level" = '3'
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

    public List<FavoriteItem> findSelectedItems(Long userId) {
        String sql = """
            SELECT country, league, team
            FROM favorites
            WHERE user_id = :userId
            ORDER BY "level", country, league, team
        """;

        return userJdbcTemplate.query(sql,
            new MapSqlParameterSource().addValue("userId", userId),
            (rs, n) -> {
                FavoriteItem item = new FavoriteItem();
                item.setCountry(rs.getString("country"));
                item.setLeague(rs.getString("league"));
                item.setTeam(rs.getString("team"));
                return item;
            }
        );
    }

    public int deleteAllByUserId(Long userId) {
        String sql = """
            DELETE FROM favorites
            WHERE user_id = :userId
            """;

        return userJdbcTemplate.update(
            sql,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("userId", userId)
        );
    }

    /**
	 * チームのお気に入り（level = 3）を id 付きで取得（お気に入り編集画面・トップ画面用）
	 */
	public List<FavoriteTeamDTO> findTeams(Long userId) {
		String sql = """
			SELECT id, country, league, team
			FROM favorites
			WHERE user_id = :userId
			  AND "level" = '3'
			ORDER BY country, league, team
			""";
		return userJdbcTemplate.query(sql, new MapSqlParameterSource().addValue("userId", userId), (rs, n) -> {
			FavoriteTeamDTO d = new FavoriteTeamDTO();
			d.setId(rs.getLong("id"));
			d.setCountry(rs.getString("country"));
			d.setLeague(rs.getString("league"));
			d.setTeam(rs.getString("team"));
			return d;
		});
	}

	/** チームのお気に入りの件数 */
	public int countTeams(Long userId) {
		String sql = "SELECT COUNT(*) FROM favorites WHERE user_id = :userId AND \"level\" = '3'";
		Integer c = userJdbcTemplate.queryForObject(sql, Map.of("userId", userId), Integer.class);
		return c == null ? 0 : c;
	}

}
