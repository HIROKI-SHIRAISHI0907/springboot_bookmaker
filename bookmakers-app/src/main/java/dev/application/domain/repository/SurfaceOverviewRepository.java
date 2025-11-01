package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import dev.application.analyze.bm_m031.SurfaceOverviewEntity;

@Mapper
public interface SurfaceOverviewRepository {

	@Lang(XMLLanguageDriver.class)
	@Insert("""
			<script>
			INSERT INTO surface_overview (
			  country, league, game_year, game_month, team,
			  games, rank, win, lose, draw, winning_points,
			  home_1st_half_score, home_2nd_half_score, home_sum_score,
			  home_1st_half_score_ratio, home_2nd_half_score_ratio, home_clean_sheet,
			  away_1st_half_score, away_2nd_half_score, away_sum_score,
			  away_1st_half_score_ratio, away_2nd_half_score_ratio, away_clean_sheet,
			  home_1st_half_lost, home_2nd_half_lost, home_sum_lost,
			  home_1st_half_lost_ratio, home_2nd_half_lost_ratio,
			  away_1st_half_lost, away_2nd_half_lost, away_sum_lost,
			  away_1st_half_lost_ratio, away_2nd_half_lost_ratio,
			  fail_to_score_game_count,
			  consecutive_win_disp, consecutive_lose_disp,
			  unbeaten_streak_count, unbeaten_streak_disp,
			  consecutive_score_count, consecutive_score_count_disp,
			  first_week_game_win_count, first_week_game_lost_count, first_week_game_win_disp,
			  mid_week_game_win_count, mid_week_game_lost_count, mid_week_game_win_disp,
			  last_week_game_win_count, last_week_game_lost_count, last_week_game_win_disp,
			  home_win_count, home_lose_count, home_first_goal_count,
			  home_win_behind_count, home_lose_behind_count,
			  home_win_behind_0vs1_count, home_lose_behind_1vs0_count,
			  home_win_behind_0vs2_count, home_lose_behind_2vs0_count,
			  home_win_behind_other_count, home_lose_behind_other_count,
			  home_adversity_disp,
			  away_win_count, away_lose_count, away_first_goal_count,
			  away_win_behind_count, away_lose_behind_count,
			  away_win_behind_1vs0_count, away_lose_behind_0vs1_count,
			  away_win_behind_2vs0_count, away_lose_behind_0vs2_count,
			  away_win_behind_other_count, away_lose_behind_other_count,
			  away_adversity_disp,
			  promote_disp, descend_disp, first_win_disp, lose_streak_disp, round_conc,
			  register_id, register_time, update_id, update_time
			) VALUES (
			  #{country}, #{league}, #{gameYear}, #{gameMonth}, #{team},
			  #{games}, #{rank}, #{win}, #{lose}, #{draw}, #{winningPoints},
			  #{home1stHalfScore}, #{home2ndHalfScore}, #{homeSumScore},
			  #{home1stHalfScoreRatio}, #{home2ndHalfScoreRatio}, #{homeCleanSheet},
			  #{away1stHalfScore}, #{away2ndHalfScore}, #{awaySumScore},
			  #{away1stHalfScoreRatio}, #{away2ndHalfScoreRatio}, #{awayCleanSheet},
			  #{home1stHalfLost}, #{home2ndHalfLost},
			  #{home1stHalfLostRatio}, #{home2ndHalfLostRatio},
			  #{away1stHalfLost}, #{away2ndHalfLost}, #{awaySumLost},
			  #{away1stHalfLostRatio}, #{away2ndHalfLostRatio},
			  #{failToScoreGameCount},
			  #{consecutiveWinDisp}, #{consecutiveLoseDisp},
			  #{unbeatenStreakCount}, #{unbeatenStreakDisp},
			  #{consecutiveScoreCount}, #{consecutiveScoreCountDisp},
			  #{firstWeekGameWinCount}, #{firstWeekGameLostCount}, #{firstWeekGameWinDisp},
			  #{midWeekGameWinCount}, #{midWeekGameLostCount}, #{midWeekGameWinDisp},
			  #{lastWeekGameWinCount}, #{lastWeekGameLostCount}, #{lastWeekGameWinDisp},
			  #{homeWinCount}, #{homeLoseCount}, #{homeFirstGoalCount},
			  #{homeWinBehindCount}, #{homeLoseBehindCount},
			  #{homeWinBehind0vs1Count}, #{homeLoseBehind1vs0Count},
			  #{homeWinBehind0vs2Count}, #{homeLoseBehind2vs0Count},
			  #{homeWinBehindOtherCount}, #{homeLoseBehindOtherCount},
			  #{homeAdversityDisp},
			  #{awayWinCount}, #{awayLoseCount}, #{awayFirstGoalCount},
			  #{awayWinBehindCount}, #{awayLoseBehindCount},
			  #{awayWinBehind1vs0Count}, #{awayLoseBehind0vs1Count},
			  #{awayWinBehind2vs0Count}, #{awayLoseBehind0vs2Count},
			  #{awayWinBehindOtherCount}, #{awayLoseBehindOtherCount},
			  #{awayAdversityDisp},
			  #{promoteDisp}, #{descendDisp}, #{firstWinDisp}, #{loseStreakDisp}, #{roundConc},
			  #{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz)
			);
			</script>
			""")
	int insert(SurfaceOverviewEntity entity);

	@Lang(XMLLanguageDriver.class)
	@Select("""
						<script>
						SELECT
						  id,
						  country,
						  league,
						  game_year,
						  game_month,
						  team,
						  games,
						  rank,
						  win,
						  lose,
						  draw,
						  winning_points,
						  home_1st_half_score,
						  home_2nd_half_score,
						  home_sum_score,
						  home_1st_half_score_ratio,
						  home_2nd_half_score_ratio,
						  home_clean_sheet,
						  away_1st_half_score,
						  away_2nd_half_score,
						  away_sum_score,
						  away_1st_half_score_ratio,
						  away_2nd_half_score_ratio,
						  away_clean_sheet,
						  home_1st_half_lost,
						  home_2nd_half_lost,
						  home_sum_lost,
			  			  home_1st_half_lost_ratio,
			  			  home_2nd_half_lost_ratio,
			  			  away_1st_half_lost,
			  			  away_2nd_half_lost,
			  			  away_sum_lost,
			              away_1st_half_lost_ratio,
			              away_2nd_half_lost_ratio,
						  fail_to_score_game_count,
						  consecutive_win_disp,
						  consecutive_lose_disp,
						  unbeaten_streak_count,
						  unbeaten_streak_disp,
						  consecutive_score_count,
						  consecutive_score_count_disp,
						  first_week_game_win_count,
						  first_week_game_lost_count,
						  first_week_game_win_disp,
						  mid_week_game_win_count,
						  mid_week_game_lost_count,
						  mid_week_game_win_disp,
						  last_week_game_win_count,
						  last_week_game_lost_count,
						  last_week_game_win_disp,
						  home_win_count,
						  home_lose_count,
						  home_first_goal_count,
						  home_win_behind_count,
						  home_lose_behind_count,
						  home_win_behind_0vs1_count,
						  home_lose_behind_1vs0_count,
						  home_win_behind_0vs2_count,
						  home_lose_behind_2vs0_count,
						  home_win_behind_other_count,
						  home_lose_behind_other_count,
						  home_adversity_disp,
						  away_win_count,
						  away_lose_count,
						  away_first_goal_count,
						  away_win_behind_count,
						  away_lose_behind_count,
						  away_win_behind_1vs0_count,
						  away_lose_behind_0vs1_count,
						  away_win_behind_2vs0_count,
						  away_lose_behind_0vs2_count,
						  away_win_behind_other_count,
						  away_lose_behind_other_count,
						  away_adversity_disp,
						  promote_disp,
						  descend_disp,
						  first_win_disp,
						  lose_streak_disp,
						  round_conc
						FROM surface_overview
						WHERE country = #{country}
						  AND league  = #{league}
						    AND team = #{team}
						    AND game_year  = #{gameYear}
							AND game_month = #{gameMonth}
						    </script>
						""")
	List<SurfaceOverviewEntity> select(String country, String league, String gameYear, String gameMonth, String team);

	@Lang(XMLLanguageDriver.class)
	@Update("""
			<script>
			UPDATE surface_overview SET
			  country = #{country},
			  league = #{league},
			  game_year = #{gameYear},
			  game_month = #{gameMonth},
			  team = #{team},
			  games = #{games},
			  rank = #{rank},
			  win = #{win},
			  lose = #{lose},
			  draw = #{draw},
			  winning_points = #{winningPoints},
			  home_1st_half_score = #{home1stHalfScore},
			  home_2nd_half_score = #{home2ndHalfScore},
			  home_sum_score = #{homeSumScore},
			  home_1st_half_score_ratio = #{home1stHalfScoreRatio},
			  home_2nd_half_score_ratio = #{home2ndHalfScoreRatio},
			  home_clean_sheet = #{homeCleanSheet},
			  away_1st_half_score = #{away1stHalfScore},
			  away_2nd_half_score = #{away2ndHalfScore},
			  away_sum_score = #{awaySumScore},
			  away_1st_half_score_ratio = #{away1stHalfScoreRatio},
			  away_2nd_half_score_ratio = #{away2ndHalfScoreRatio},
			  away_clean_sheet = #{awayCleanSheet},
			  home_1st_half_lost = #{home1stHalfLost},
			  home_2nd_half_lost = #{home2ndHalfLost},
			  home_1st_half_lost_ratio = #{home1stHalfLostRatio},
			  home_2nd_half_lost_ratio = #{home2ndHalfLostRatio},
			  away_1st_half_lost = #{away1stHalfLost},
			  away_2nd_half_lost = #{away2ndHalfLost},
			  away_sum_lost = #{awaySumLost},
			  away_1st_half_lost_ratio = #{away1stHalfLostRatio},
			  away_2nd_half_lost_ratio = #{away2ndHalfLostRatio},
			  fail_to_score_game_count = #{failToScoreGameCount},
			  consecutive_win_disp = #{consecutiveWinDisp},
			  consecutive_lose_disp = #{consecutiveLoseDisp},
			  unbeaten_streak_count = #{unbeatenStreakCount},
			  unbeaten_streak_disp = #{unbeatenStreakDisp},
			  consecutive_score_count = #{consecutiveScoreCount},
			  consecutive_score_count_disp = #{consecutiveScoreCountDisp},
			  first_week_game_win_count = #{firstWeekGameWinCount},
			  first_week_game_lost_count = #{firstWeekGameLostCount},
			  first_week_game_win_disp = #{firstWeekGameWinDisp},
			  mid_week_game_win_count = #{midWeekGameWinCount},
			  mid_week_game_lost_count = #{midWeekGameLostCount},
			  mid_week_game_win_disp = #{midWeekGameWinDisp},
			  last_week_game_win_count = #{lastWeekGameWinCount},
			  last_week_game_lost_count = #{lastWeekGameLostCount},
			  last_week_game_win_disp = #{lastWeekGameWinDisp},
			  home_win_count = #{homeWinCount},
			  home_lose_count = #{homeLoseCount},
			  home_first_goal_count = #{homeFirstGoalCount},
			  home_win_behind_count = #{homeWinBehindCount},
			  home_lose_behind_count = #{homeLoseBehindCount},
			  home_win_behind_0vs1_count = #{homeWinBehind0vs1Count},
			  home_lose_behind_1vs0_count = #{homeLoseBehind1vs0Count},
			  home_win_behind_0vs2_count = #{homeWinBehind0vs2Count},
			  home_lose_behind_2vs0_count = #{homeLoseBehind2vs0Count},
			  home_win_behind_other_count = #{homeWinBehindOtherCount},
			  home_lose_behind_other_count = #{homeLoseBehindOtherCount},
			  home_adversity_disp = #{homeAdversityDisp},
			  away_win_count = #{awayWinCount},
			  away_lose_count = #{awayLoseCount},
			  away_first_goal_count = #{awayFirstGoalCount},
			  away_win_behind_count = #{awayWinBehindCount},
			  away_lose_behind_count = #{awayLoseBehindCount},
			  away_win_behind_1vs0_count = #{awayWinBehind1vs0Count},
			  away_lose_behind_0vs1_count = #{awayLoseBehind0vs1Count},
			  away_win_behind_2vs0_count = #{awayWinBehind2vs0Count},
			  away_lose_behind_0vs2_count = #{awayLoseBehind0vs2Count},
			  away_win_behind_other_count = #{awayWinBehindOtherCount},
			  away_lose_behind_other_count = #{awayLoseBehindOtherCount},
			  away_adversity_disp = #{awayAdversityDisp},
			  promote_disp = #{promoteDisp},
			  descend_disp = #{descendDisp},
			  first_win_disp = #{firstWinDisp},
			  lose_streak_disp = #{loseStreakDisp},
			  round_conc = #{roundConc}
			WHERE id = CAST(#{id,jdbcType=VARCHAR} AS INTEGER)
			</script>
			""")
	int update(SurfaceOverviewEntity entity);

	@Lang(XMLLanguageDriver.class)
	@Select("""
			<script>
			SELECT
			    id,
			    country,
			    league,
			    game_year,
			    game_month,
			    team,
			    games,
			    rank,
			    win,
			    lose,
			    draw,
			    winning_points              AS winningPoints,
			    home_1st_half_score         AS home1stHalfScore,
			    home_2nd_half_score         AS home2ndHalfScore,
			    home_sum_score              AS homeSumScore,
			    home_1st_half_score_ratio   AS home1stHalfScoreRatio,
			    home_2nd_half_score_ratio   AS home2ndHalfScoreRatio,
			    home_clean_sheet            AS homeCleanSheet,
			    away_1st_half_score         AS away1stHalfScore,
			    away_2nd_half_score         AS away2ndHalfScore,
			    away_sum_score              AS awaySumScore,
			    away_1st_half_score_ratio   AS away1stHalfScoreRatio,
			    away_2nd_half_score_ratio   AS away2ndHalfScoreRatio,
			    away_clean_sheet            AS awayCleanSheet,
			    fail_to_score_game_count    AS failToScoreGameCount,
			    consecutive_win_disp        AS consecutiveWinDisp,
			    consecutive_lose_disp       AS consecutiveLoseDisp,
			    unbeaten_streak_count       AS unbeatenStreakCount,
			    unbeaten_streak_disp        AS unbeatenStreakDisp,
			    consecutive_score_count     AS consecutiveScoreCount,
			    consecutive_score_count_disp AS consecutiveScoreCountDisp,
			    first_week_game_win_count   AS firstWeekGameWinCount,
			    first_week_game_lost_count  AS firstWeekGameLostCount,
			    first_week_game_win_disp    AS firstWeekGameWinDisp,
			    mid_week_game_win_count     AS midWeekGameWinCount,
			    mid_week_game_lost_count    AS midWeekGameLostCount,
			    mid_week_game_win_disp      AS midWeekGameWinDisp,
			    last_week_game_win_count    AS lastWeekGameWinCount,
			    last_week_game_lost_count   AS lastWeekGameLostCount,
			    last_week_game_win_disp     AS lastWeekGameWinDisp,
			    home_win_count              AS homeWinCount,
			    home_lose_count             AS homeLoseCount,
			    home_first_goal_count       AS homeFirstGoalCount,
			    home_win_behind_count       AS homeWinBehindCount,
			    home_lose_behind_count      AS homeLoseBehindCount,
			    home_win_behind_0vs1_count  AS homeWinBehind0vs1Count,
			    home_lose_behind_1vs0_count AS homeLoseBehind1vs0Count,
			    home_win_behind_0vs2_count  AS homeWinBehind0vs2Count,
			    home_lose_behind_2vs0_count AS homeLoseBehind2vs0Count,
			    home_win_behind_other_count AS homeWinBehindOtherCount,
			    home_lose_behind_other_count AS homeLoseBehindOtherCount,
			    home_adversity_disp         AS homeAdversityDisp,
			    away_win_count              AS awayWinCount,
			    away_lose_count             AS awayLoseCount,
			    away_first_goal_count       AS awayFirstGoalCount,
			    away_win_behind_count       AS awayWinBehindCount,
			    away_lose_behind_count      AS awayLoseBehindCount,
			    away_win_behind_1vs0_count  AS awayWinBehind1vs0Count,
			    away_lose_behind_0vs1_count AS awayLoseBehind0vs1Count,
			    away_win_behind_2vs0_count  AS awayWinBehind2vs0Count,
			    away_lose_behind_0vs2_count AS awayLoseBehind0vs2Count,
			    away_win_behind_other_count AS awayWinBehindOtherCount,
			    away_lose_behind_other_count AS awayLoseBehindOtherCount,
			    away_adversity_disp         AS awayAdversityDisp,
			    promote_disp                AS promoteDisp,
			    descend_disp                AS descendDisp,
			    first_win_disp              AS firstWinDisp,
			    lose_streak_disp            AS loseStreakDisp,
			    -- ★ DBに列が無いので固定で NULL を返す（アプリ側で roundConc から再計算）
			    NULL                        AS consecutiveLoseCount,
			    round_conc                  AS roundConc
			FROM surface_overview
			WHERE country = #{country}
			  AND league  = #{league}
			  AND team    = #{team}
			ORDER BY
			    game_year,
			    game_month
			</script>
			""")
	List<SurfaceOverviewEntity> selectAllMonthsByTeam(String country, String league, String team);

	@Select("""
			SELECT *
			FROM surface_overview
			""")
	List<SurfaceOverviewEntity> getData();

}
