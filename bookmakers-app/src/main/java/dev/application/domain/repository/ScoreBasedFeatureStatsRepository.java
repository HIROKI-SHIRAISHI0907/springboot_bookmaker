package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m023.ScoreBasedFeatureStatsEntity;

@Mapper
public interface ScoreBasedFeatureStatsRepository {

    @Insert({
        "INSERT INTO average_feature_data (",
        "id, situation, score, country, league, home_exp_stat, away_exp_stat, home_donation_stat, away_donation_stat,",
        "home_shoot_all_stat, away_shoot_all_stat, home_shoot_in_stat, away_shoot_in_stat, home_shoot_out_stat, away_shoot_out_stat,",
        "home_block_shoot_stat, away_block_shoot_stat, home_big_chance_stat, away_big_chance_stat, home_corner_stat, away_corner_stat,",
        "home_box_shoot_in_stat, away_box_shoot_in_stat, home_box_shoot_out_stat, away_box_shoot_out_stat, home_goal_post_stat, away_goal_post_stat,",
        "home_goal_head_stat, away_goal_head_stat, home_keeper_save_stat, away_keeper_save_stat, home_free_kick_stat, away_free_kick_stat,",
        "home_offside_stat, away_offside_stat, home_foul_stat, away_foul_stat, home_yellow_card_stat, away_yellow_card_stat,",
        "home_red_card_stat, away_red_card_stat, home_slow_in_stat, away_slow_in_stat, home_box_touch_stat, away_box_touch_stat,",
        "home_pass_count_stat, away_pass_count_stat, home_final_third_pass_count_stat, away_final_third_pass_count_stat,",
        "home_cross_count_stat, away_cross_count_stat, register_id, register_time, update_id, update_time",
        ") VALUES (",
        "#{id}, #{situation}, #{score}, #{country}, #{league}, #{homeExpStat}, #{awayExpStat}, #{homeDonationStat}, #{awayDonationStat},",
        "#{homeShootAllStat}, #{awayShootAllStat}, #{homeShootInStat}, #{awayShootInStat}, #{homeShootOutStat}, #{awayShootOutStat},",
        "#{homeBlockShootStat}, #{awayBlockShootStat}, #{homeBigChanceStat}, #{awayBigChanceStat}, #{homeCornerStat}, #{awayCornerStat},",
        "#{homeBoxShootInStat}, #{awayBoxShootInStat}, #{homeBoxShootOutStat}, #{awayBoxShootOutStat}, #{homeGoalPostStat}, #{awayGoalPostStat},",
        "#{homeGoalHeadStat}, #{awayGoalHeadStat}, #{homeKeeperSaveStat}, #{awayKeeperSaveStat}, #{homeFreeKickStat}, #{awayFreeKickStat},",
        "#{homeOffsideStat}, #{awayOffsideStat}, #{homeFoulStat}, #{awayFoulStat}, #{homeYellowCardStat}, #{awayYellowCardStat},",
        "#{homeRedCardStat}, #{awayRedCardStat}, #{homeSlowInStat}, #{awaySlowInStat}, #{homeBoxTouchStat}, #{awayBoxTouchStat},",
        "#{homePassCountStat}, #{awayPassCountStat}, #{homeFinalThirdPassCountStat}, #{awayFinalThirdPassCountStat},",
        "#{homeCrossCountStat}, #{awayCrossCountStat}, #{registerId},#{registerTime},#{updateId},#{updateTime}",
        ")"
    })
    void insert(ScoreBasedFeatureStatsEntity entity);
}
