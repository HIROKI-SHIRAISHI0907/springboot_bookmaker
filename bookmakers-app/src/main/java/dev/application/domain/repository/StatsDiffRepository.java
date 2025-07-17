package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;

import dev.application.analyze.bm_m022.StatsDiffEntity;

public interface StatsDiffRepository {

    @Insert("""
        INSERT INTO scoring_playstyle_past_data (
            id, data_category, home_team_name, away_team_name,
            diff_home_score, diff_away_score,
            diff_home_donation, diff_away_donation,
            diff_home_shoot_all, diff_away_shoot_all,
            diff_home_shoot_in, diff_away_shoot_in,
            diff_home_shoot_out, diff_away_shoot_out,
            diff_home_block_shoot, diff_away_block_shoot,
            diff_home_big_chance, diff_away_big_chance,
            diff_home_corner, diff_away_corner,
            diff_home_box_shoot_in, diff_away_box_shoot_in,
            diff_home_box_shoot_out, diff_away_box_shoot_out,
            diff_home_goal_post, diff_away_goal_post,
            diff_home_goal_head, diff_away_goal_head,
            diff_home_keeper_save, diff_away_keeper_save,
            diff_home_free_kick, diff_away_free_kick,
            diff_home_offside, diff_away_offside,
            diff_home_foul, diff_away_foul,
            diff_home_yellow_card, diff_away_yellow_card,
            diff_home_red_card, diff_away_red_card,
            diff_home_slow_in, diff_away_slow_in,
            diff_home_box_touch, diff_away_box_touch,
            diff_home_pass_count, diff_away_pass_count,
            diff_home_final_third_pass_count, diff_away_final_third_pass_count,
            diff_home_cross_count, diff_away_cross_count,
            diff_home_tackle_count, diff_away_tackle_count,
            diff_home_clear_count, diff_away_clear_count,
            diff_home_intercept_count, diff_away_intercept_count,
            home_play_style, away_play_style,
            register_id, register_time, update_id, update_time
        ) VALUES (
            #{id}, #{dataCategory}, #{homeTeamName}, #{awayTeamName},
            #{diffHomeScore}, #{diffAwayScore},
            #{diffHomeDonation}, #{diffAwayDonation},
            #{diffHomeShootAll}, #{diffAwayShootAll},
            #{diffHomeShootIn}, #{diffAwayShootIn},
            #{diffHomeShootOut}, #{diffAwayShootOut},
            #{diffHomeBlockShoot}, #{diffAwayBlockShoot},
            #{diffHomeBigChance}, #{diffAwayBigChance},
            #{diffHomeCorner}, #{diffAwayCorner},
            #{diffHomeBoxShootIn}, #{diffAwayBoxShootIn},
            #{diffHomeBoxShootOut}, #{diffAwayBoxShootOut},
            #{diffHomeGoalPost}, #{diffAwayGoalPost},
            #{diffHomeGoalHead}, #{diffAwayGoalHead},
            #{diffHomeKeeperSave}, #{diffAwayKeeperSave},
            #{diffHomeFreeKick}, #{diffAwayFreeKick},
            #{diffHomeOffside}, #{diffAwayOffside},
            #{diffHomeFoul}, #{diffAwayFoul},
            #{diffHomeYellowCard}, #{diffAwayYellowCard},
            #{diffHomeRedCard}, #{diffAwayRedCard},
            #{diffHomeSlowIn}, #{diffAwaySlowIn},
            #{diffHomeBoxTouch}, #{diffAwayBoxTouch},
            #{diffHomePassCount}, #{diffAwayPassCount},
            #{diffHomeFinalThirdPassCount}, #{diffAwayFinalThirdPassCount},
            #{diffHomeCrossCount}, #{diffAwayCrossCount},
            #{diffHomeTackleCount}, #{diffAwayTackleCount},
            #{diffHomeClearCount}, #{diffAwayClearCount},
            #{diffHomeInterceptCount}, #{diffAwayInterceptCount},
            #{homePlayStyle}, #{awayPlayStyle},
            #{registerId}, #{registerTime}, #{updateId}, #{updateTime}
        )
    """)
    void insert(StatsDiffEntity entity);
}
