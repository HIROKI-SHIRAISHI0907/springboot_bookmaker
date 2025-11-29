package dev.web.repository;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w004.ScheduledSurfaceSnapshotDTO;

/**
 * ScheduledOverviewsRepositoryクラス
 * surface_overview から開催予定用スナップショットを集計して返却する。
 *
 * Node 実装: scheduled_overviews.ts の fetchLatest 相当。
 *
 * @author shiraishitoshio
 */
@Repository
public class ScheduledOverviewsRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public ScheduledOverviewsRepository(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    /**
     * 指定されたチーム・役割（home / away）について、
     * surface_overview の全レコードを対象に月別集計したスナップショットを返す。
     *
     * ベース SQL は Node 実装 scheduled_overviews.ts の CTE を移植。
     *
     * @param country 国名
     * @param league  リーグ名
     * @param teamName チーム名（surface_overview.team）
     * @param role    "home" または "away"
     * @return スナップショット。該当が無ければ null。
     */
    public ScheduledSurfaceSnapshotDTO findLatestSnapshot(
            String country,
            String league,
            String teamName,
            String role
    ) {

        String sql = """
            WITH base AS (
              SELECT *
              FROM public.surface_overview
              WHERE
                BTRIM(country) = BTRIM(:country)
                AND BTRIM(league)  = BTRIM(:league)
                AND BTRIM(team)    = BTRIM(:team)
            ),

            -- 月別の「この役割でどの列を足すか」
            monthly AS (
              SELECT
                NULLIF(BTRIM(game_year),  '')::int  AS game_year,
                NULLIF(BTRIM(game_month), '')::int  AS game_month,
                CASE WHEN :role = 'home'
                  THEN COALESCE(NULLIF(BTRIM(home_sum_score),   '')::int, 0)
                  ELSE COALESCE(NULLIF(BTRIM(away_sum_score),   '')::int, 0)
                END AS goals_for_m,
                CASE WHEN :role = 'home'
                  THEN COALESCE(NULLIF(BTRIM(home_clean_sheet), '')::int, 0)
                  ELSE COALESCE(NULLIF(BTRIM(away_clean_sheet), '')::int, 0)
                END AS clean_sheets_m,
                CASE WHEN :role = 'home'
                  THEN COALESCE(NULLIF(BTRIM(home_1st_half_score), '')::int, 0)
                  ELSE COALESCE(NULLIF(BTRIM(away_1st_half_score), '')::int, 0)
                END AS first_half_m,
                CASE WHEN :role = 'home'
                  THEN COALESCE(NULLIF(BTRIM(home_2nd_half_score), '')::int, 0)
                  ELSE COALESCE(NULLIF(BTRIM(away_2nd_half_score), '')::int, 0)
                END AS second_half_m,

                CASE WHEN :role = 'home'
                  THEN COALESCE(NULLIF(BTRIM(home_first_goal_count), '' )::int, 0)
                  ELSE COALESCE(NULLIF(BTRIM(away_first_goal_count), '' )::int, 0)
                END AS first_goal_m,

                CASE WHEN :role = 'home'
                  THEN COALESCE(NULLIF(BTRIM(home_win_behind_count), '' )::int, 0)
                  ELSE COALESCE(NULLIF(BTRIM(away_win_behind_count), '' )::int, 0)
                END AS win_behind_m,

                CASE WHEN :role = 'home'
                  THEN COALESCE(NULLIF(BTRIM(home_lose_behind_count), '' )::int, 0)
                  ELSE COALESCE(NULLIF(BTRIM(away_lose_behind_count), '' )::int, 0)
                END AS lose_behind_m
              FROM base
            ),

            agg AS (
              SELECT
                SUM(COALESCE(NULLIF(BTRIM(games),  '')::int, 0))::int AS games,
                SUM(COALESCE(NULLIF(BTRIM(win),    '')::int, 0))::int AS win,
                SUM(COALESCE(NULLIF(BTRIM(draw),   '')::int, 0))::int AS draw,
                SUM(COALESCE(NULLIF(BTRIM(lose),   '')::int, 0))::int AS lose,
                SUM(COALESCE(NULLIF(BTRIM(winning_points), '')::int, 0))::int AS winning_points,

                SUM(goals_for_m)::int      AS goals_for,
                SUM(clean_sheets_m)::int   AS clean_sheets,
                SUM(first_half_m)::int     AS first_half_score,
                SUM(second_half_m)::int    AS second_half_score,
                SUM(first_goal_m)::int     AS first_goal_count,
                SUM(win_behind_m)::int     AS win_behind_count,
                SUM(lose_behind_m)::int    AS lose_behind_count,

                -- 役割別の勝敗数
                SUM(CASE WHEN :role = 'home'
                      THEN COALESCE(NULLIF(BTRIM(home_win_count), '' )::int, 0)
                      ELSE COALESCE(NULLIF(BTRIM(away_win_count), '' )::int, 0)
                    END)::int AS win_count_role,
                SUM(CASE WHEN :role = 'home'
                      THEN COALESCE(NULLIF(BTRIM(home_lose_count), '' )::int, 0)
                      ELSE COALESCE(NULLIF(BTRIM(away_lose_count), '' )::int, 0)
                    END)::int AS lose_count_role,

                -- 役割に依存しない
                SUM(COALESCE(NULLIF(BTRIM(fail_to_score_game_count), '' )::int, 0))::int AS fail_to_score_game_count
              FROM base b
              JOIN monthly m
                ON m.game_year  = NULLIF(BTRIM(b.game_year),  '')::int
               AND m.game_month = NULLIF(BTRIM(b.game_month), '')::int
            ),

            latest AS (
              SELECT
                BTRIM(team)::text AS team,
                NULLIF(BTRIM(game_year),  '')::int  AS game_year,
                NULLIF(BTRIM(game_month), '')::int  AS game_month,
                consecutive_win_disp,
                consecutive_lose_disp,
                unbeaten_streak_disp,
                consecutive_score_count_disp,
                first_week_game_win_disp,
                mid_week_game_win_disp,
                last_week_game_win_disp,
                lose_streak_disp,
                promote_disp,
                descend_disp,
                home_adversity_disp,
                away_adversity_disp
              FROM base
              ORDER BY NULLIF(BTRIM(game_year),  '')::int DESC,
                       NULLIF(BTRIM(game_month), '')::int DESC
              LIMIT 1
            )

            SELECT
              latest.team, latest.game_year, latest.game_month,
              agg.games, agg.win, agg.draw, agg.lose, agg.winning_points,
              agg.goals_for, agg.clean_sheets, agg.first_half_score, agg.second_half_score,
              agg.first_goal_count, agg.win_behind_count, agg.lose_behind_count,
              agg.win_count_role, agg.lose_count_role, agg.fail_to_score_game_count,
              latest.consecutive_win_disp, latest.consecutive_lose_disp, latest.unbeaten_streak_disp,
              latest.consecutive_score_count_disp, latest.first_week_game_win_disp, latest.mid_week_game_win_disp,
              latest.last_week_game_win_disp, latest.lose_streak_disp, latest.promote_disp, latest.descend_disp,
              latest.home_adversity_disp, latest.away_adversity_disp
            FROM agg CROSS JOIN latest
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("team", teamName)
                .addValue("role", role);

        List<ScheduledSurfaceSnapshotDTO> list = namedJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> {
                    ScheduledSurfaceSnapshotDTO s = new ScheduledSurfaceSnapshotDTO();
                    s.setTeam(rs.getString("team"));
                    s.setGameYear((Integer) rs.getObject("game_year"));
                    s.setGameMonth((Integer) rs.getObject("game_month"));

                    // rank は Node 側でも null 固定だったのでここでは未集計（必要なら latest.rank をSQLに追加）
                    s.setRank(null);

                    s.setGames((Integer) rs.getObject("games"));
                    s.setWin((Integer) rs.getObject("win"));
                    s.setDraw((Integer) rs.getObject("draw"));
                    s.setLose((Integer) rs.getObject("lose"));
                    s.setWinningPoints((Integer) rs.getObject("winning_points"));

                    s.setGoalsFor((Integer) rs.getObject("goals_for"));
                    s.setCleanSheets((Integer) rs.getObject("clean_sheets"));
                    s.setFirstHalfScore((Integer) rs.getObject("first_half_score"));
                    s.setSecondHalfScore((Integer) rs.getObject("second_half_score"));

                    s.setFirstGoalCount((Integer) rs.getObject("first_goal_count"));
                    s.setWinBehindCount((Integer) rs.getObject("win_behind_count"));
                    s.setLoseBehindCount((Integer) rs.getObject("lose_behind_count"));
                    s.setWinCountRole((Integer) rs.getObject("win_count_role"));
                    s.setLoseCountRole((Integer) rs.getObject("lose_count_role"));
                    s.setFailToScoreGameCount((Integer) rs.getObject("fail_to_score_game_count"));

                    // camelCase 用の 4 つは、現状は null 固定（フロント側 fallback 用）
                    s.setHomeWinCount(null);
                    s.setHomeLoseCount(null);
                    s.setAwayWinCount(null);
                    s.setAwayLoseCount(null);

                    s.setConsecutiveWinDisp(rs.getString("consecutive_win_disp"));
                    s.setConsecutiveLoseDisp(rs.getString("consecutive_lose_disp"));
                    s.setUnbeatenStreakDisp(rs.getString("unbeaten_streak_disp"));
                    s.setConsecutiveScoreCountDisp(rs.getString("consecutive_score_count_disp"));
                    s.setFirstWeekGameWinDisp(rs.getString("first_week_game_win_disp"));
                    s.setMidWeekGameWinDisp(rs.getString("mid_week_game_win_disp"));
                    s.setLastWeekGameWinDisp(rs.getString("last_week_game_win_disp"));
                    s.setFirstWinDisp(rs.getString("first_win_disp"));          // SQL 上に無い列なら null のまま
                    s.setLoseStreakDisp(rs.getString("lose_streak_disp"));
                    s.setPromoteDisp(rs.getString("promote_disp"));
                    s.setDescendDisp(rs.getString("descend_disp"));
                    s.setHomeAdversityDisp(rs.getString("home_adversity_disp"));
                    s.setAwayAdversityDisp(rs.getString("away_adversity_disp"));

                    return s;
                }
        );

        return list.isEmpty() ? null : list.get(0);
    }
}
