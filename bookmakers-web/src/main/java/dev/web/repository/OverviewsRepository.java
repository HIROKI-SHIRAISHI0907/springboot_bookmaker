package dev.web.repository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_w003.OverviewResponseDTO;
import dev.web.api.bm_w003.ScheduleMatchDTO;
import dev.web.api.bm_w003.SurfaceSnapshotDTO;

/**
 * OverviewsRepositoryクラス
 * @author shiraishitoshio
 *
 */
@Repository
public class OverviewsRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;

    public OverviewsRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate,
            @Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate
    ) {
        this.bmJdbcTemplate = bmJdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
    }

    // --------------------------------------------------------
    // 取得: GET /api/:country/:league/:team/overview（月ごとのデータ）
    // --------------------------------------------------------
    public String findTeamJa(String country, String league, String teamSlug) {
        String sql = """
            SELECT team
            FROM country_league_master
            WHERE country = :country
              AND league  = :league
              AND split_part(NULLIF(link,''), '/', 3) = :slug
            LIMIT 1
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("slug", teamSlug);

        List<String> results = masterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> rs.getString("team")
        );

        return results.isEmpty() ? teamSlug : results.get(0);
    }

    // --------------------------------------------------------
    // 取得: GET /api/:country/:league/match/:seq（概要詳細データ）
    // --------------------------------------------------------
    public List<OverviewResponseDTO> findMonthlyOverview(String country, String league, String teamJa) {

        String sql = """
        WITH base AS (
          SELECT
            o.game_year::int  AS year,
            o.game_month::int AS month,

            SUM(COALESCE(NULLIF(BTRIM(o.winning_points), '')::int, 0)) AS winning_points,
            SUM(COALESCE(NULLIF(BTRIM(o.games), '' )::int, 0)) AS games_raw,
            SUM(COALESCE(NULLIF(BTRIM(o.win),   '' )::int, 0)) AS wins,
            SUM(COALESCE(NULLIF(BTRIM(o.draw),  '' )::int, 0)) AS draws,
            SUM(COALESCE(NULLIF(BTRIM(o.lose),  '' )::int, 0)) AS loses,

            SUM(COALESCE(NULLIF(BTRIM(o.home_sum_score),  '')::int, 0)
              + COALESCE(NULLIF(BTRIM(o.away_sum_score),  '')::int, 0)) AS goals_for,
            SUM(COALESCE(NULLIF(BTRIM(o.home_clean_sheet), '')::int, 0)
              + COALESCE(NULLIF(BTRIM(o.away_clean_sheet), '')::int, 0)) AS clean_sheets,

            SUM(COALESCE(NULLIF(BTRIM(o.home_sum_score),  '')::int, 0)) AS home_goals_for,
            SUM(COALESCE(NULLIF(BTRIM(o.home_1st_half_score), '')::int, 0)) AS home_goals_1st,
            SUM(COALESCE(NULLIF(BTRIM(o.home_2nd_half_score), '')::int, 0)) AS home_goals_2nd,
            SUM(COALESCE(NULLIF(BTRIM(o.home_clean_sheet), '')::int, 0)) AS home_clean_sheets,
            SUM(COALESCE(NULLIF(BTRIM(o.home_win_count), '')::int, 0)) AS home_wins,
            SUM(COALESCE(NULLIF(BTRIM(o.home_lose_count), '')::int, 0)) AS home_loses,
            SUM(COALESCE(NULLIF(BTRIM(o.home_first_goal_count), '')::int, 0)) AS home_first_goals,

            SUM(COALESCE(NULLIF(BTRIM(o.away_sum_score),  '')::int, 0)) AS away_goals_for,
            SUM(COALESCE(NULLIF(BTRIM(o.away_1st_half_score), '')::int, 0)) AS away_goals_1st,
            SUM(COALESCE(NULLIF(BTRIM(o.away_2nd_half_score), '')::int, 0)) AS away_goals_2nd,
            SUM(COALESCE(NULLIF(BTRIM(o.away_clean_sheet), '')::int, 0)) AS away_clean_sheets,
            SUM(COALESCE(NULLIF(BTRIM(o.away_win_count), '')::int, 0)) AS away_wins,
            SUM(COALESCE(NULLIF(BTRIM(o.away_lose_count), '')::int, 0)) AS away_loses,
            SUM(COALESCE(NULLIF(BTRIM(o.away_first_goal_count), '')::int, 0)) AS away_first_goals

          FROM public.surface_overview o
          WHERE o.country = :country
            AND o.league  = :league
            AND o.team    = :team
          GROUP BY o.game_year, o.game_month
        )
        SELECT
          year, month,
          winning_points,
          CASE WHEN SUM(games_raw) OVER () IS NULL OR games_raw = 0
            THEN (wins + draws + loses)
            ELSE games_raw
          END AS games,
          wins, draws, loses,
          goals_for, clean_sheets,
          home_goals_for, home_goals_1st, home_goals_2nd, home_clean_sheets, home_wins, home_loses, home_first_goals,
          away_goals_for, away_goals_1st, away_goals_2nd, away_clean_sheets, away_wins, away_loses, away_first_goals
        FROM base
        ORDER BY year ASC, month ASC
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("team", teamJa);

        RowMapper<OverviewResponseDTO> rowMapper = (rs, rowNum) -> {
        	OverviewResponseDTO dto = new OverviewResponseDTO();
            int year = rs.getInt("year");
            int month = rs.getInt("month");

            dto.setYear(year);
            dto.setMonth(month);
            dto.setYm(year + "-" + String.format("%02d", month));
            dto.setLabel(String.format("%02d月", month));

            dto.setWinningPoints(rs.getInt("winning_points"));
            dto.setGames(rs.getInt("games"));
            dto.setWin(rs.getInt("wins"));
            dto.setDraw(rs.getInt("draws"));
            dto.setLose(rs.getInt("loses"));

            dto.setGoalsFor(rs.getInt("goals_for"));
            dto.setCleanSheets(rs.getInt("clean_sheets"));

            // goalsAgainst は SQL に無いので、必要なら別で計算 or 列追加
            dto.setGoalsAgainst(0); // とりあえず 0。必要なら実ロジックに合わせてください。

            return dto;
        };

        return bmJdbcTemplate.query(sql, params, rowMapper);
    }

    // --------------------------------------------------------
    // 取得: GET /api/:country/:league/match/:seq（マッチデータ）
    // --------------------------------------------------------
    public ScheduleMatchDTO findMatch(String country, String league, long seq) {
        String sql = """
          SELECT
            m.seq::int,
            m.round_no,
            m.future_time,
            m.game_year::int,
            m.game_month::int,
            m.home_team,
            m.away_team,
            m.link
          FROM public.future_master m
          WHERE m.seq = :seq
            AND m.country = :country
            AND m.league  = :league
          LIMIT 1
          """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("seq", seq)
                .addValue("country", country)
                .addValue("league", league);

        List<ScheduleMatchDTO> list = masterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> {
                    ScheduleMatchDTO m = new ScheduleMatchDTO();
                    m.setSeq(rs.getLong("seq"));
                    m.setRoundNo((Integer) rs.getObject("round_no"));
                    m.setFutureTime(rs.getString("future_time"));
                    m.setGameYear(rs.getInt("game_year"));
                    m.setGameMonth(rs.getInt("game_month"));
                    m.setHomeTeam(rs.getString("home_team"));
                    m.setAwayTeam(rs.getString("away_team"));
                    m.setLink(rs.getString("link"));
                    return m;
                }
        );

        return list.isEmpty() ? null : list.get(0);
    }

    // --------------------------------------------------------
    // 取得: GET /api/:country/:league/match/:seq
    // --------------------------------------------------------
    public List<SurfaceSnapshotDTO> findSurfacesForMatch(
            String country,
            String league,
            int gameYear,
            int gameMonth,
            String homeTeam,
            String awayTeam
    ) {

        String sql = """
        WITH base AS (
          SELECT
            team,
            SUM(COALESCE(NULLIF(BTRIM(games), '' )::int, 0)) AS games,
            SUM(COALESCE(NULLIF(BTRIM(win),   '' )::int, 0)) AS win,
            SUM(COALESCE(NULLIF(BTRIM(draw),  '' )::int, 0)) AS draw,
            SUM(COALESCE(NULLIF(BTRIM(lose),  '' )::int, 0)) AS lose,
            SUM(COALESCE(NULLIF(BTRIM(winning_points), '' )::int, 0)) AS winning_points,
            MAX(consecutive_win_disp) AS consecutive_win_disp,
            MAX(consecutive_lose_disp) AS consecutive_lose_disp,
            MAX(unbeaten_streak_disp) AS unbeaten_streak_disp,
            MAX(consecutive_score_count_disp) AS consecutive_score_count_disp,
            MAX(first_win_disp) AS first_win_disp,
            MAX(lose_streak_disp) AS lose_streak_disp,
            MAX(promote_disp) AS promote_disp,
            MAX(descend_disp) AS descend_disp,
            MAX(home_adversity_disp) AS home_adversity_disp,
            MAX(away_adversity_disp) AS away_adversity_disp
          FROM public.surface_overview
          WHERE country = :country
            AND league  = :league
            AND game_year  = :gameYear
            AND game_month = :gameMonth
            AND team IN (:homeTeam, :awayTeam)
          GROUP BY team
        )
        SELECT * FROM base
        ORDER BY team ASC
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("country", country)
                .addValue("league", league)
                .addValue("gameYear", gameYear)
                .addValue("gameMonth", gameMonth)
                .addValue("homeTeam", homeTeam)
                .addValue("awayTeam", awayTeam);

        List<SurfaceSnapshotDTO> raw = bmJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> {
                    SurfaceSnapshotDTO s = new SurfaceSnapshotDTO();
                    s.setTeam(rs.getString("team"));
                    s.setGames((Integer) rs.getObject("games"));
                    s.setWin((Integer) rs.getObject("win"));
                    s.setDraw((Integer) rs.getObject("draw"));
                    s.setLose((Integer) rs.getObject("lose"));
                    s.setWinningPoints((Integer) rs.getObject("winning_points"));

                    s.setConsecutiveWinDisp(rs.getString("consecutive_win_disp"));
                    s.setConsecutiveLoseDisp(rs.getString("consecutive_lose_disp"));
                    s.setUnbeatenStreakDisp(rs.getString("unbeaten_streak_disp"));
                    s.setConsecutiveScoreCountDisp(rs.getString("consecutive_score_count_disp"));
                    s.setFirstWeekGameWinDisp(rs.getString("first_week_game_win_disp"));
                    s.setMidWeekGameWinDisp(rs.getString("mid_week_game_win_disp"));
                    s.setLastWeekGameWinDisp(rs.getString("last_week_game_win_disp"));
                    s.setFirstWinDisp(rs.getString("first_win_disp"));
                    s.setLoseStreakDisp(rs.getString("lose_streak_disp"));
                    s.setPromoteDisp(rs.getString("promote_disp"));
                    s.setDescendDisp(rs.getString("descend_disp"));
                    s.setHomeAdversityDisp(rs.getString("home_adversity_disp"));
                    s.setAwayAdversityDisp(rs.getString("away_adversity_disp"));
                    return s;
                }
        );

        // home, away の順に並べ替え
        return List.of(homeTeam, awayTeam).stream()
                .map(t -> raw.stream().filter(s -> t.equals(s.getTeam())).findFirst().orElse(null))
                .filter(s -> s != null)
                .collect(Collectors.toList());
    }
}
