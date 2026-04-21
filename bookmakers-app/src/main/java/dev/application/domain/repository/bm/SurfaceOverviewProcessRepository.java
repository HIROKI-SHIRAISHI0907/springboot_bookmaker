package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m032.SurfaceOverviewProcessEntity;

/**
 * surface_overview_process テーブルのRepository。
 *
 * <p>
 * 同一チームの直前ラウンドとの差分データを保持する。
 * </p>
 *
 * @author shiraishitoshio
 */
@Mapper
public interface SurfaceOverviewProcessRepository {

	/**
	 * 差分データを新規登録する。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO surface_overview_process (
				country,
				league,
				game_year,
				game_month,
				team,
				before_round_conc,
				after_round_conc,
				previous_round_no,
				current_round_no,
				round_gap,
				games_diff,
				win_diff,
				lose_diff,
				draw_diff,
				winning_points_diff,
				home_1st_half_score_diff,
				home_2nd_half_score_diff,
				home_sum_score_diff,
				away_1st_half_score_diff,
				away_2nd_half_score_diff,
				away_sum_score_diff,
				home_1st_half_lost_diff,
				home_2nd_half_lost_diff,
				home_sum_lost_diff,
				away_1st_half_lost_diff,
				away_2nd_half_lost_diff,
				away_sum_lost_diff,
				fail_to_score_game_count_diff,
				first_week_game_win_count_diff,
				first_week_game_lost_count_diff,
				mid_week_game_win_count_diff,
				mid_week_game_lost_count_diff,
				last_week_game_win_count_diff,
				last_week_game_lost_count_diff,
				home_win_count_diff,
				home_lose_count_diff,
				home_first_goal_count_diff,
				home_win_behind_count_diff,
				home_lose_behind_count_diff,
				away_win_count_diff,
				away_lose_count_diff,
				away_first_goal_count_diff,
				away_win_behind_count_diff,
				away_lose_behind_count_diff,
				register_id,
				register_time,
				update_id,
				update_time
			) VALUES (
				#{country},
				#{league},
				#{gameYear},
				#{gameMonth},
				#{team},
				#{beforeRoundConc},
				#{afterRoundConc},
				#{previousRoundNo},
				#{currentRoundNo},
				#{roundGap},
				#{gamesDiff},
				#{winDiff},
				#{loseDiff},
				#{drawDiff},
				#{winningPointsDiff},
				#{home1stHalfScoreDiff},
				#{home2ndHalfScoreDiff},
				#{homeSumScoreDiff},
				#{away1stHalfScoreDiff},
				#{away2ndHalfScoreDiff},
				#{awaySumScoreDiff},
				#{home1stHalfLostDiff},
				#{home2ndHalfLostDiff},
				#{homeSumLostDiff},
				#{away1stHalfLostDiff},
				#{away2ndHalfLostDiff},
				#{awaySumLostDiff},
				#{failToScoreGameCountDiff},
				#{firstWeekGameWinCountDiff},
				#{firstWeekGameLostCountDiff},
				#{midWeekGameWinCountDiff},
				#{midWeekGameLostCountDiff},
				#{lastWeekGameWinCountDiff},
				#{lastWeekGameLostCountDiff},
				#{homeWinCountDiff},
				#{homeLoseCountDiff},
				#{homeFirstGoalCountDiff},
				#{homeWinBehindCountDiff},
				#{homeLoseBehindCountDiff},
				#{awayWinCountDiff},
				#{awayLoseCountDiff},
				#{awayFirstGoalCountDiff},
				#{awayWinBehindCountDiff},
				#{awayLoseBehindCountDiff},
				#{registerId},
				CAST(#{registerTime} AS timestamptz),
				#{updateId},
				CAST(#{updateTime} AS timestamptz)
			)
			""")
	int insert(SurfaceOverviewProcessEntity entity);

	/**
	 * 主キー相当条件で差分データを取得する。
	 *
	 * @param country 国
	 * @param league リーグ
	 * @param gameYear 試合年
	 * @param gameMonth 試合月
	 * @param team チーム
	 * @param currentRoundNo 今回ラウンド番号
	 * @return 該当データ
	 */
	@Select("""
			SELECT
				country,
				league,
				game_year AS gameYear,
				game_month AS gameMonth,
				team,
				before_round_conc AS beforeRoundConc,
				after_round_conc AS afterRoundConc,
				previous_round_no AS previousRoundNo,
				current_round_no AS currentRoundNo,
				round_gap AS roundGap,
				games_diff AS gamesDiff,
				win_diff AS winDiff,
				lose_diff AS loseDiff,
				draw_diff AS drawDiff,
				winning_points_diff AS winningPointsDiff,
				home_1st_half_score_diff AS home1stHalfScoreDiff,
				home_2nd_half_score_diff AS home2ndHalfScoreDiff,
				home_sum_score_diff AS homeSumScoreDiff,
				away_1st_half_score_diff AS away1stHalfScoreDiff,
				away_2nd_half_score_diff AS away2ndHalfScoreDiff,
				away_sum_score_diff AS awaySumScoreDiff,
				home_1st_half_lost_diff AS home1stHalfLostDiff,
				home_2nd_half_lost_diff AS home2ndHalfLostDiff,
				home_sum_lost_diff AS homeSumLostDiff,
				away_1st_half_lost_diff AS away1stHalfLostDiff,
				away_2nd_half_lost_diff AS away2ndHalfLostDiff,
				away_sum_lost_diff AS awaySumLostDiff,
				fail_to_score_game_count_diff AS failToScoreGameCountDiff,
				first_week_game_win_count_diff AS firstWeekGameWinCountDiff,
				first_week_game_lost_count_diff AS firstWeekGameLostCountDiff,
				mid_week_game_win_count_diff AS midWeekGameWinCountDiff,
				mid_week_game_lost_count_diff AS midWeekGameLostCountDiff,
				last_week_game_win_count_diff AS lastWeekGameWinCountDiff,
				last_week_game_lost_count_diff AS lastWeekGameLostCountDiff,
				home_win_count_diff AS homeWinCountDiff,
				home_lose_count_diff AS homeLoseCountDiff,
				home_first_goal_count_diff AS homeFirstGoalCountDiff,
				home_win_behind_count_diff AS homeWinBehindCountDiff,
				home_lose_behind_count_diff AS homeLoseBehindCountDiff,
				away_win_count_diff AS awayWinCountDiff,
				away_lose_count_diff AS awayLoseCountDiff,
				away_first_goal_count_diff AS awayFirstGoalCountDiff,
				away_win_behind_count_diff AS awayWinBehindCountDiff,
				away_lose_behind_count_diff AS awayLoseBehindCountDiff
			FROM
				surface_overview_process
			WHERE
				country = #{country}
				AND league = #{league}
				AND game_year = #{gameYear}
				AND game_month = #{gameMonth}
				AND team = #{team}
				AND current_round_no = #{currentRoundNo};
			""")
	List<SurfaceOverviewProcessEntity> findByKey(
			@Param("country") String country,
			@Param("league") String league,
			@Param("gameYear") Integer gameYear,
			@Param("gameMonth") Integer gameMonth,
			@Param("team") String team,
			@Param("currentRoundNo") Integer currentRoundNo);

	/**
	 * 差分データを更新する。
	 *
	 * @param entity 更新対象
	 * @return 更新件数
	 */
	@Update("""
			UPDATE surface_overview_process
			SET
				before_round_conc = #{beforeRoundConc},
				after_round_conc = #{afterRoundConc},
				previous_round_no = #{previousRoundNo},
				round_gap = #{roundGap},
				games_diff = #{gamesDiff},
				win_diff = #{winDiff},
				lose_diff = #{loseDiff},
				draw_diff = #{drawDiff},
				winning_points_diff = #{winningPointsDiff},
				home_1st_half_score_diff = #{home1stHalfScoreDiff},
				home_2nd_half_score_diff = #{home2ndHalfScoreDiff},
				home_sum_score_diff = #{homeSumScoreDiff},
				away_1st_half_score_diff = #{away1stHalfScoreDiff},
				away_2nd_half_score_diff = #{away2ndHalfScoreDiff},
				away_sum_score_diff = #{awaySumScoreDiff},
				home_1st_half_lost_diff = #{home1stHalfLostDiff},
				home_2nd_half_lost_diff = #{home2ndHalfLostDiff},
				home_sum_lost_diff = #{homeSumLostDiff},
				away_1st_half_lost_diff = #{away1stHalfLostDiff},
				away_2nd_half_lost_diff = #{away2ndHalfLostDiff},
				away_sum_lost_diff = #{awaySumLostDiff},
				fail_to_score_game_count_diff = #{failToScoreGameCountDiff},
				first_week_game_win_count_diff = #{firstWeekGameWinCountDiff},
				first_week_game_lost_count_diff = #{firstWeekGameLostCountDiff},
				mid_week_game_win_count_diff = #{midWeekGameWinCountDiff},
				mid_week_game_lost_count_diff = #{midWeekGameLostCountDiff},
				last_week_game_win_count_diff = #{lastWeekGameWinCountDiff},
				last_week_game_lost_count_diff = #{lastWeekGameLostCountDiff},
				home_win_count_diff = #{homeWinCountDiff},
				home_lose_count_diff = #{homeLoseCountDiff},
				home_first_goal_count_diff = #{homeFirstGoalCountDiff},
				home_win_behind_count_diff = #{homeWinBehindCountDiff},
				home_lose_behind_count_diff = #{homeLoseBehindCountDiff},
				away_win_count_diff = #{awayWinCountDiff},
				away_lose_count_diff = #{awayLoseCountDiff},
				away_first_goal_count_diff = #{awayFirstGoalCountDiff},
				away_win_behind_count_diff = #{awayWinBehindCountDiff},
				away_lose_behind_count_diff = #{awayLoseBehindCountDiff},
				update_id = #{updateId},
				update_time = CAST(#{updateTime} AS timestamptz)
			WHERE
				country = #{country}
				AND league = #{league}
				AND game_year = #{gameYear}
				AND game_month = #{gameMonth}
				AND team = #{team}
				AND current_round_no = #{currentRoundNo};
			""")
	int update(SurfaceOverviewProcessEntity entity);
}
