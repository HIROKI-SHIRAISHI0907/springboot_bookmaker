package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureEntity;

@Mapper
public interface EachTeamScoreBasedFeatureStatsRepository {

    @Insert({
        "INSERT INTO each_team_score_based_feature_stats (",
        "situation, score, country, league, team, home_exp_stat, away_exp_stat, home_in_goal_exp_stat, away_in_goal_exp_stat, home_donation_stat, away_donation_stat,",
        "home_shoot_all_stat, away_shoot_all_stat, home_shoot_in_stat, away_shoot_in_stat, home_shoot_out_stat, away_shoot_out_stat,",
        "home_block_shoot_stat, away_block_shoot_stat, home_big_chance_stat, away_big_chance_stat, home_corner_stat, away_corner_stat,",
        "home_box_shoot_in_stat, away_box_shoot_in_stat, home_box_shoot_out_stat, away_box_shoot_out_stat, home_goal_post_stat, away_goal_post_stat,",
        "home_goal_head_stat, away_goal_head_stat, home_keeper_save_stat, away_keeper_save_stat, home_free_kick_stat, away_free_kick_stat,",
        "home_offside_stat, away_offside_stat, home_foul_stat, away_foul_stat, home_yellow_card_stat, away_yellow_card_stat,",
        "home_red_card_stat, away_red_card_stat, home_slow_in_stat, away_slow_in_stat, home_box_touch_stat, away_box_touch_stat,",
        "home_pass_count_stat, away_pass_count_stat, home_final_third_pass_count_stat, away_final_third_pass_count_stat,",
        "home_cross_count_stat, away_cross_count_stat, home_tackle_count_stat, away_tackle_count_stat,",
        "home_clear_count_stat, away_clear_count_stat, home_intercept_count_stat, away_intercept_count_stat,",
        "register_id, register_time, update_id, update_time",
        ") VALUES (",
        "#{situation}, #{score}, #{country}, #{league}, #{team}, #{homeExpStat}, #{awayExpStat}, #{homeInGoalExpStat}, #{awayInGoalExpStat}, #{homeDonationStat}, #{awayDonationStat},",
        "#{homeShootAllStat}, #{awayShootAllStat}, #{homeShootInStat}, #{awayShootInStat}, #{homeShootOutStat}, #{awayShootOutStat},",
        "#{homeBlockShootStat}, #{awayBlockShootStat}, #{homeBigChanceStat}, #{awayBigChanceStat}, #{homeCornerStat}, #{awayCornerStat},",
        "#{homeBoxShootInStat}, #{awayBoxShootInStat}, #{homeBoxShootOutStat}, #{awayBoxShootOutStat}, #{homeGoalPostStat}, #{awayGoalPostStat},",
        "#{homeGoalHeadStat}, #{awayGoalHeadStat}, #{homeKeeperSaveStat}, #{awayKeeperSaveStat}, #{homeFreeKickStat}, #{awayFreeKickStat},",
        "#{homeOffsideStat}, #{awayOffsideStat}, #{homeFoulStat}, #{awayFoulStat}, #{homeYellowCardStat}, #{awayYellowCardStat},",
        "#{homeRedCardStat}, #{awayRedCardStat}, #{homeSlowInStat}, #{awaySlowInStat}, #{homeBoxTouchStat}, #{awayBoxTouchStat},",
        "#{homePassCountStat}, #{awayPassCountStat}, #{homeFinalThirdPassCountStat}, #{awayFinalThirdPassCountStat},",
        "#{homeCrossCountStat}, #{awayCrossCountStat}, #{homeTackleCountStat}, #{awayTackleCountStat},",
        "#{homeClearCountStat}, #{awayClearCountStat}, #{homeInterceptCountStat}, #{awayInterceptCountStat},",
        "#{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz)",
        ")"
    })
    int insert(EachTeamScoreBasedFeatureEntity entity);

    @Select({
        "SELECT id,",
        "home_exp_stat, away_exp_stat, home_in_goal_exp_stat, away_in_goal_exp_stat, home_donation_stat, away_donation_stat,",
        "home_shoot_all_stat, away_shoot_all_stat, home_shoot_in_stat, away_shoot_in_stat,",
        "home_shoot_out_stat, away_shoot_out_stat, home_block_shoot_stat, away_block_shoot_stat,",
        "home_big_chance_stat, away_big_chance_stat, home_corner_stat, away_corner_stat,",
        "home_box_shoot_in_stat, away_box_shoot_in_stat, home_box_shoot_out_stat, away_box_shoot_out_stat,",
        "home_goal_post_stat, away_goal_post_stat, home_goal_head_stat, away_goal_head_stat,",
        "home_keeper_save_stat, away_keeper_save_stat, home_free_kick_stat, away_free_kick_stat,",
        "home_offside_stat, away_offside_stat, home_foul_stat, away_foul_stat,",
        "home_yellow_card_stat, away_yellow_card_stat, home_red_card_stat, away_red_card_stat,",
        "home_slow_in_stat, away_slow_in_stat, home_box_touch_stat, away_box_touch_stat,",
        "home_pass_count_stat, away_pass_count_stat, home_final_third_pass_count_stat, away_final_third_pass_count_stat,",
        "home_cross_count_stat, away_cross_count_stat, home_tackle_count_stat, away_tackle_count_stat,",
        "home_clear_count_stat, away_clear_count_stat, home_intercept_count_stat, away_intercept_count_stat",
        "FROM each_team_score_based_feature_stats ",
        "WHERE situation = #{situation} AND score = #{score} AND country = #{country} AND league = #{league} AND "
        + "team = #{team};"
    })
    List<EachTeamScoreBasedFeatureEntity> findStatData(@Param("score") String score, @Param("situation") String situation,
    		@Param("country") String country, @Param("league") String league, @Param("team") String team);

    @Select({
        "SELECT id,",
        "home_exp_stat, away_exp_stat, home_in_goal_exp_stat, away_in_goal_exp_stat, home_donation_stat, away_donation_stat,",
        "home_shoot_all_stat, away_shoot_all_stat, home_shoot_in_stat, away_shoot_in_stat,",
        "home_shoot_out_stat, away_shoot_out_stat, home_block_shoot_stat, away_block_shoot_stat,",
        "home_big_chance_stat, away_big_chance_stat, home_corner_stat, away_corner_stat,",
        "home_box_shoot_in_stat, away_box_shoot_in_stat, home_box_shoot_out_stat, away_box_shoot_out_stat,",
        "home_goal_post_stat, away_goal_post_stat, home_goal_head_stat, away_goal_head_stat,",
        "home_keeper_save_stat, away_keeper_save_stat, home_free_kick_stat, away_free_kick_stat,",
        "home_offside_stat, away_offside_stat, home_foul_stat, away_foul_stat,",
        "home_yellow_card_stat, away_yellow_card_stat, home_red_card_stat, away_red_card_stat,",
        "home_slow_in_stat, away_slow_in_stat, home_box_touch_stat, away_box_touch_stat,",
        "home_pass_count_stat, away_pass_count_stat, home_final_third_pass_count_stat, away_final_third_pass_count_stat,",
        "home_cross_count_stat, away_cross_count_stat, home_tackle_count_stat, away_tackle_count_stat,",
        "home_clear_count_stat, away_clear_count_stat, home_intercept_count_stat, away_intercept_count_stat",
        "FROM each_team_score_based_feature_stats ",
        "WHERE country = #{country} AND league = #{league} AND "
        + "team = #{team};"
    })
    List<EachTeamScoreBasedFeatureEntity> findData(@Param("country") String country, @Param("league") String league, @Param("team") String team);

    @Select({
        "SELECT id, score, country, league, team,",
        "home_exp_stat, away_exp_stat, home_in_goal_exp_stat, away_in_goal_exp_stat, home_donation_stat, away_donation_stat,",
        "home_shoot_all_stat, away_shoot_all_stat, home_shoot_in_stat, away_shoot_in_stat,",
        "home_shoot_out_stat, away_shoot_out_stat, home_block_shoot_stat, away_block_shoot_stat,",
        "home_big_chance_stat, away_big_chance_stat, home_corner_stat, away_corner_stat,",
        "home_box_shoot_in_stat, away_box_shoot_in_stat, home_box_shoot_out_stat, away_box_shoot_out_stat,",
        "home_goal_post_stat, away_goal_post_stat, home_goal_head_stat, away_goal_head_stat,",
        "home_keeper_save_stat, away_keeper_save_stat, home_free_kick_stat, away_free_kick_stat,",
        "home_offside_stat, away_offside_stat, home_foul_stat, away_foul_stat,",
        "home_yellow_card_stat, away_yellow_card_stat, home_red_card_stat, away_red_card_stat,",
        "home_slow_in_stat, away_slow_in_stat, home_box_touch_stat, away_box_touch_stat,",
        "home_pass_count_stat, away_pass_count_stat, home_final_third_pass_count_stat, away_final_third_pass_count_stat,",
        "home_cross_count_stat, away_cross_count_stat, home_tackle_count_stat, away_tackle_count_stat,",
        "home_clear_count_stat, away_clear_count_stat, home_intercept_count_stat, away_intercept_count_stat",
        "FROM each_team_score_based_feature_stats;"
    })
    List<EachTeamScoreBasedFeatureEntity> findAllStatData();

    @Update({
        "UPDATE each_team_score_based_feature_stats SET",
        "home_exp_stat = #{homeExpStat},",
        "away_exp_stat = #{awayExpStat},",
        "home_in_goal_exp_stat = #{homeInGoalExpStat},",
        "away_in_goal_exp_stat = #{awayInGoalExpStat},",
        "home_donation_stat = #{homeDonationStat},",
        "away_donation_stat = #{awayDonationStat},",
        "home_shoot_all_stat = #{homeShootAllStat},",
        "away_shoot_all_stat = #{awayShootAllStat},",
        "home_shoot_in_stat = #{homeShootInStat},",
        "away_shoot_in_stat = #{awayShootInStat},",
        "home_shoot_out_stat = #{homeShootOutStat},",
        "away_shoot_out_stat = #{awayShootOutStat},",
        "home_block_shoot_stat = #{homeBlockShootStat},",
        "away_block_shoot_stat = #{awayBlockShootStat},",
        "home_big_chance_stat = #{homeBigChanceStat},",
        "away_big_chance_stat = #{awayBigChanceStat},",
        "home_corner_stat = #{homeCornerStat},",
        "away_corner_stat = #{awayCornerStat},",
        "home_box_shoot_in_stat = #{homeBoxShootInStat},",
        "away_box_shoot_in_stat = #{awayBoxShootInStat},",
        "home_box_shoot_out_stat = #{homeBoxShootOutStat},",
        "away_box_shoot_out_stat = #{awayBoxShootOutStat},",
        "home_goal_post_stat = #{homeGoalPostStat},",
        "away_goal_post_stat = #{awayGoalPostStat},",
        "home_goal_head_stat = #{homeGoalHeadStat},",
        "away_goal_head_stat = #{awayGoalHeadStat},",
        "home_keeper_save_stat = #{homeKeeperSaveStat},",
        "away_keeper_save_stat = #{awayKeeperSaveStat},",
        "home_free_kick_stat = #{homeFreeKickStat},",
        "away_free_kick_stat = #{awayFreeKickStat},",
        "home_offside_stat = #{homeOffsideStat},",
        "away_offside_stat = #{awayOffsideStat},",
        "home_foul_stat = #{homeFoulStat},",
        "away_foul_stat = #{awayFoulStat},",
        "home_yellow_card_stat = #{homeYellowCardStat},",
        "away_yellow_card_stat = #{awayYellowCardStat},",
        "home_red_card_stat = #{homeRedCardStat},",
        "away_red_card_stat = #{awayRedCardStat},",
        "home_slow_in_stat = #{homeSlowInStat},",
        "away_slow_in_stat = #{awaySlowInStat},",
        "home_box_touch_stat = #{homeBoxTouchStat},",
        "away_box_touch_stat = #{awayBoxTouchStat},",
        "home_pass_count_stat = #{homePassCountStat},",
        "away_pass_count_stat = #{awayPassCountStat},",
        "home_final_third_pass_count_stat = #{homeFinalThirdPassCountStat},",
        "away_final_third_pass_count_stat = #{awayFinalThirdPassCountStat},",
        "home_cross_count_stat = #{homeCrossCountStat},",
        "away_cross_count_stat = #{awayCrossCountStat},",
        "home_tackle_count_stat = #{homeTackleCountStat},",
        "away_tackle_count_stat = #{awayTackleCountStat},",
        "home_clear_count_stat = #{homeClearCountStat},",
        "away_clear_count_stat = #{awayClearCountStat},",
        "home_intercept_count_stat = #{homeInterceptCountStat},",
        "away_intercept_count_stat = #{awayInterceptCountStat} ",
        "WHERE id = CAST(#{id,jdbcType=VARCHAR} AS INTEGER);"
    })
    int updateStatValues(EachTeamScoreBasedFeatureEntity entity);
}
