package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w014.CountryLeagueSeasonDTO;
import dev.web.api.bm_w014.CountryLeagueSeasonSearchCondition;

/**
 * CountryLeagueSeasonMasterRepository
 * @author shiraishitoshio
 */
@Repository
public class CountryLeagueSeasonMasterRepository {

    private final NamedParameterJdbcTemplate masterJdbcTemplate;

    public CountryLeagueSeasonMasterRepository(
            @Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate
    ) {
        this.masterJdbcTemplate = masterJdbcTemplate;
    }

    // ---- 全件 ----
    public List<CountryLeagueSeasonDTO> findAll() {
        String sql = """
            SELECT
              id,
              country,
              league,
              season_year,
              path,
              del_flg
            FROM country_league_season_master
            ORDER BY country, league, season_year
        """;

        return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
            CountryLeagueSeasonDTO dto = new CountryLeagueSeasonDTO();
            dto.setCountry(rs.getString("country"));
            dto.setLeague(rs.getString("league"));
            dto.setSeasonYear(rs.getString("season_year"));
            dto.setPath(rs.getString("path"));
            dto.setDelFlg(rs.getString("del_flg"));
            return dto;
        });
    }

    // ---- 条件検索（IF）----
    public List<CountryLeagueSeasonDTO> search(CountryLeagueSeasonSearchCondition cond) {

        StringBuilder sql = new StringBuilder("""
            SELECT
              id,
              country,
              league,
              season_year,
              path,
              del_flg
            FROM country_league_season_master
            WHERE 1=1
        """);

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (hasText(cond.getCountry())) {
            sql.append(" AND country = :country ");
            params.addValue("country", cond.getCountry());
        }
        if (hasText(cond.getLeague())) {
            sql.append(" AND league = :league ");
            params.addValue("league", cond.getLeague());
        }
        if (hasText(cond.getSeasonYear())) {
            sql.append(" AND season_year = :seasonYear ");
            params.addValue("seasonYear", cond.getSeasonYear());
        }
        if (hasText(cond.getPath())) {
            // 部分一致
            sql.append(" AND path LIKE :path ");
            params.addValue("path", "%" + cond.getPath() + "%");
        }
        if (hasText(cond.getDelFlg())) {
            sql.append(" AND del_flg = :delFlg ");
            params.addValue("delFlg", cond.getDelFlg());
        }

        sql.append(" ORDER BY country, league ");

        return masterJdbcTemplate.query(sql.toString(), params, (rs, n) -> {
            CountryLeagueSeasonDTO dto = new CountryLeagueSeasonDTO();
            dto.setCountry(rs.getString("country"));
            dto.setLeague(rs.getString("league"));
            dto.setSeasonYear(rs.getString("season_year"));
            dto.setPath(rs.getString("path"));
            dto.setDelFlg(rs.getString("del_flg"));
            return dto;
        });
    }

    // ---- 更新（path更新）----
    public int updatePath(String country, String league, String seasonYear, String path) {
        String sql = """
            UPDATE country_league_season_master
            SET
              path = :path,
              season_year = :seasonYear,
              del_flg = '0'
            WHERE
              country = :country
              AND league = :league
        """;

        return masterJdbcTemplate.update(
            sql,
            new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("seasonYear", seasonYear)
                .addValue("path", path)
        );
    }

    // ---- path重複チェック（自分自身は除外）----
    public boolean existsPathOtherThanKey(String country, String league, String seasonYear, String path) {
        String sql = """
            SELECT COUNT(*)
            FROM country_league_season_master
            WHERE path = :path
              AND NOT (
                   country = :country
               AND league  = :league
               AND season_year = :seasonYear
              )
        """;

        Integer cnt = masterJdbcTemplate.queryForObject(
            sql,
            new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("seasonYear", seasonYear)
                .addValue("path", path),
            Integer.class
        );

        return cnt != null && cnt > 0;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
