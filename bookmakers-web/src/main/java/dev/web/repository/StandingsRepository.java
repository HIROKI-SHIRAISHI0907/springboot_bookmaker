package dev.web.repository;

import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w006.StandingRowDTO;

/**
 * 順位表取得用リポジトリ.
 *
 * /api/{country}/{league}/standings
 * で利用される。
 *
 * - public.surface_overview からチームごとに集計
 * - country_league_master から link を取得し、slug (teamEnglish) を切り出す
 * - 並び順:
 *   勝点 DESC → 得失点差 DESC → 勝利 DESC → 敗戦 ASC → チーム名 ASC
 *
 * @author shiraishitoshio
 */
@Repository
public class StandingsRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public StandingsRepository(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    /**
     * 国・リーグを指定して順位表を取得する。
     *
     * @param country 国名（日本語）
     * @param league リーグ名（日本語）
     * @return 順位表行一覧
     */
    public List<StandingRowDTO> findStandings(String country, String league) {

        String sql = """
            WITH base AS (
              SELECT
                o.team,
                SUM(COALESCE(NULLIF(BTRIM(o.win),  '')::int, 0))   AS win,
                SUM(COALESCE(NULLIF(BTRIM(o.lose), '')::int, 0))   AS lose,
                SUM(COALESCE(NULLIF(BTRIM(o.draw), '')::int, 0))   AS draw,
                SUM(COALESCE(NULLIF(BTRIM(o.winning_points), '')::int, 0)) AS points,
                -- 総得点（ホーム+アウェイ）
                SUM(
                  COALESCE(NULLIF(BTRIM(o.home_sum_score), '')::int, 0)
                  + COALESCE(NULLIF(BTRIM(o.away_sum_score), '')::int, 0)
                ) AS goals_for,
                -- 総失点（ホーム+アウェイ）
                SUM(
                  COALESCE(NULLIF(BTRIM(o.home_sum_lost), '')::int, 0)
                  + COALESCE(NULLIF(BTRIM(o.away_sum_lost), '')::int, 0)
                ) AS goals_against
              FROM public.surface_overview o
              WHERE o.country = :country
                AND o.league  = :league
              GROUP BY o.team
            ),
            with_meta AS (
              SELECT
                b.*,
                (b.win + b.draw + b.lose) AS game,
                (b.goals_for - b.goals_against) AS goal_diff,
                c.link
              FROM base b
              LEFT JOIN (
                SELECT team, MIN(NULLIF(TRIM(link), '')) AS link
                FROM country_league_master
                WHERE country = :country AND league = :league
                GROUP BY team
              ) c ON c.team = b.team
            ),
            ranked AS (
              SELECT
                ROW_NUMBER() OVER (
                  ORDER BY points DESC, goal_diff DESC, win DESC, lose ASC, team ASC
                ) AS position,
                *
              FROM with_meta
            )
            SELECT
              r.position,
              r.team AS team_name,
              COALESCE(split_part(NULLIF(r.link,''), '/', 3), '') AS team_english,
              r.game,
              r.win,
              r.draw,
              r.lose,
              r.points,
              r.goal_diff
            FROM ranked r
            ORDER BY r.points DESC, r.goal_diff DESC
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league);

        RowMapper<StandingRowDTO> rowMapper = (rs, rowNum) -> {
            StandingRowDTO dto = new StandingRowDTO();
            dto.setPosition(rs.getInt("position"));
            dto.setTeamName(rs.getString("team_name"));
            dto.setTeamEnglish(rs.getString("team_english") == null ? "" : rs.getString("team_english"));
            dto.setGame(rs.getInt("game"));
            dto.setWin(rs.getInt("win"));
            dto.setDraw(rs.getInt("draw"));
            dto.setLose(rs.getInt("lose"));
            dto.setWinningPoints(rs.getInt("points"));
            dto.setGoalDiff(rs.getInt("goal_diff"));
            return dto;
        };

        return namedJdbcTemplate.query(sql, params, rowMapper);
    }
}
