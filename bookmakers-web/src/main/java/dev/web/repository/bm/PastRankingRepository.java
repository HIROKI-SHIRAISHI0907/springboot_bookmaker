package dev.web.repository.bm;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w006.TeamStandingsRowDTO;

@Repository
public class PastRankingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PastRankingRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate
            // ↑ past_ranking が存在するDBに繋がる JdbcTemplate 名に合わせてください
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * past_ranking から「全節×全チーム」の推移データを rank 付きで取得する
     * rank は節ごとに付与（PARTITION BY match）
     */
    public List<TeamStandingsRowDTO> findTrendAllTeams(String country, String league,
    		String seasonYear, List<String> teamList) {

    	if (teamList == null || teamList.isEmpty()) {
            return List.of();
        }

        String sql = """
            SELECT
              country,
              league,
              season_year AS season_year,
              "match"     AS match,
              row_number() OVER (
                PARTITION BY season_year, "match"
                ORDER BY winning_points DESC, win DESC, draw DESC, team ASC
              ) AS rank,
              team,
              win,
              lose,
              draw,
              winning_points AS winning_points
            FROM past_ranking
            WHERE country = :country
              AND league  = :league
              AND season_year = :seasonYear
              AND team IN (:teamList)
            ORDER BY "match" ASC, rank ASC
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("seasonYear", seasonYear)
                .addValue("teamList", teamList);

        return jdbcTemplate.query(sql, params, (rs, n) -> {
            TeamStandingsRowDTO dto = new TeamStandingsRowDTO();
            dto.setCountry(rs.getString("country"));
            dto.setLeague(rs.getString("league"));
            dto.setSeasonYear(rs.getString("season_year"));
            dto.setMatch(getNullableInt(rs, "match"));
            dto.setRank(getNullableInt(rs, "rank"));
            dto.setTeam(rs.getString("team"));
            dto.setWin(getNullableInt(rs, "win"));
            dto.setLose(getNullableInt(rs, "lose"));
            dto.setDraw(getNullableInt(rs, "draw"));
            dto.setWinningPoints(getNullableInt(rs, "winning_points"));
            return dto;
        });
    }

    public List<TeamStandingsRowDTO> findLatestSnapshotAllTeams(
    		String country, String league, String seasonYear, List<String> teamList) {

    	if (teamList == null || teamList.isEmpty()) {
            return List.of();
        }

        String sql = """
            WITH latest AS (
              SELECT DISTINCT ON (team)
                country,
                league,
                season_year,
                "match",
                team,
                win,
                lose,
                draw,
                winning_points
              FROM past_ranking
              WHERE country = :country
                AND league  = :league
                AND season_year = :seasonYear
                AND team IN (:teamList)
              ORDER BY team, "match" DESC
            )
            SELECT
              country,
              league,
              season_year AS season_year,
              "match"     AS match,
              row_number() OVER (
                ORDER BY winning_points DESC, win DESC, draw DESC, team ASC
              ) AS rank,
              team,
              win,
              lose,
              draw,
              winning_points AS winning_points
            FROM latest
            ORDER BY rank ASC
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("country", country == null ? null : country.trim())
            .addValue("league", league == null ? null : league.trim())
            .addValue("seasonYear", seasonYear == null ? null : seasonYear.trim())
            .addValue("teamList", teamList);

        return jdbcTemplate.query(sql, params, (rs, n) -> {
            TeamStandingsRowDTO dto = new TeamStandingsRowDTO();
            dto.setCountry(rs.getString("country"));
            dto.setLeague(rs.getString("league"));
            dto.setSeasonYear(rs.getString("season_year"));
            dto.setMatch(getNullableInt(rs, "match"));           // ★ここに入る match はチームごとに違う
            dto.setRank(getNullableInt(rs, "rank"));
            dto.setTeam(rs.getString("team"));
            dto.setWin(getNullableInt(rs, "win"));
            dto.setLose(getNullableInt(rs, "lose"));
            dto.setDraw(getNullableInt(rs, "draw"));
            dto.setWinningPoints(getNullableInt(rs, "winning_points"));
            return dto;
        });
    }

    private static Integer getNullableInt(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
