package dev.web.repository.bm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * RankHistoryRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class RankHistoryRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

    public RankHistoryRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate
    ) {
        this.bmJdbcTemplate = bmJdbcTemplate;
    }

    public static class RankHistoryRow {
        public Long id;
        public String country;
        public String league;
        public String seasonYear;
        public Integer match;
        public String team;
        public Integer rank;
    }

    private static class RankHistoryRowMapper implements RowMapper<RankHistoryRow> {
        @Override
        public RankHistoryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            RankHistoryRow r = new RankHistoryRow();
            r.id = rs.getLong("id");
            r.country = rs.getString("country");
            r.league = rs.getString("league");
            r.seasonYear = rs.getString("season_year");

            // aliasで取得（match_no）
            int m = rs.getInt("match_no");
            r.match = rs.wasNull() ? null : m;

            r.team = rs.getString("team");

            int rk = rs.getInt("rank");
            r.rank = rs.wasNull() ? null : rk;

            return r;
        }
    }

    public List<RankHistoryRow> findRankHistory(String country, String league, String seasonYear) {

        String sql = """
            SELECT
              id,
              country,
              league,
              season_year,
              "match" AS match_no,
              team,
              rank
            FROM rank_history
            WHERE country = :country
              AND league  = :league
            ORDER BY match_no ASC, rank ASC
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league);

        if (seasonYear != null && !seasonYear.isBlank()) {
            sql += " AND season_year = :seasonYear ";
            params.addValue("seasonYear", seasonYear);
        }

        sql += " ORDER BY match_no ASC, rank ASC ";

        return bmJdbcTemplate.query(sql, params, new RankHistoryRowMapper());
    }
}
