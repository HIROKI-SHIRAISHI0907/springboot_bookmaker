package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m033.TeamPoints;
import dev.common.entity.DataEntity;

@Mapper
public interface BookDataRepository {

	@Insert("""
			INSERT INTO data (
			    /* ★seq を含めない★ */
			    condition_result_data_seq_id,
			    data_category,
			    times,
			    home_rank,
			    home_team_name,
			    home_score,
			    away_rank,
			    away_team_name,
			    away_score,
			    home_exp,
			    away_exp,
			    home_in_goal_exp,
			    away_in_goal_exp,
			    home_donation,
			    away_donation,
			    home_shoot_all,
			    away_shoot_all,
			    home_shoot_in,
			    away_shoot_in,
			    home_shoot_out,
			    away_shoot_out,
			    home_block_shoot,
			    away_block_shoot,
			    home_big_chance,
			    away_big_chance,
			    home_corner,
			    away_corner,
			    home_box_shoot_in,
			    away_box_shoot_in,
			    home_box_shoot_out,
			    away_box_shoot_out,
			    home_goal_post,
			    away_goal_post,
			    home_goal_head,
			    away_goal_head,
			    home_keeper_save,
			    away_keeper_save,
			    home_free_kick,
			    away_free_kick,
			    home_offside,
			    away_offside,
			    home_foul,
			    away_foul,
			    home_yellow_card,
			    away_yellow_card,
			    home_red_card,
			    away_red_card,
			    home_slow_in,
			    away_slow_in,
			    home_box_touch,
			    away_box_touch,
			    home_pass_count,
			    away_pass_count,
			    home_final_third_pass_count,
			    away_final_third_pass_count,
			    home_cross_count,
			    away_cross_count,
			    home_tackle_count,
			    away_tackle_count,
			    home_clear_count,
			    away_clear_count,
			    home_intercept_count,
			    away_intercept_count,
			    record_time,
			    weather,
			    temparature,
			    humid,
			    judge_member,
			    home_manager,
			    away_manager,
			    home_formation,
			    away_formation,
			    studium,
			    capacity,
			    audience,
			    home_max_getting_scorer,
			    away_max_getting_scorer,
			    home_max_getting_scorer_game_situation,
			    away_max_getting_scorer_game_situation,
			    home_team_home_score,
			    home_team_home_lost,
			    away_team_home_score,
			    away_team_home_lost,
			    home_team_away_score,
			    home_team_away_lost,
			    away_team_away_score,
			    away_team_away_lost,
			    notice_flg,
			    goal_time,
			    goal_team_member,
			    judge,
			    home_team_style,
			    away_team_style,
			    probablity,
			    prediction_score_time,
			    game_id,
			    match_id,
			 time_sort_seconds,
			 add_manual_flg,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES (
			    #{conditionResultDataSeqId},
			    #{dataCategory},
			    #{times},
			    #{homeRank},
			    #{homeTeamName},
			    #{homeScore},
			    #{awayRank},
			    #{awayTeamName},
			    #{awayScore},
			    #{homeExp},
			    #{awayExp},
			    #{homeInGoalExp},
			    #{awayInGoalExp},
			    #{homeDonation},
			    #{awayDonation},
			    #{homeShootAll},
			    #{awayShootAll},
			    #{homeShootIn},
			    #{awayShootIn},
			    #{homeShootOut},
			    #{awayShootOut},
			    #{homeBlockShoot},
			    #{awayBlockShoot},
			    #{homeBigChance},
			    #{awayBigChance},
			    #{homeCorner},
			    #{awayCorner},
			    #{homeBoxShootIn},
			    #{awayBoxShootIn},
			    #{homeBoxShootOut},
			    #{awayBoxShootOut},
			    #{homeGoalPost},
			    #{awayGoalPost},
			    #{homeGoalHead},
			    #{awayGoalHead},
			    #{homeKeeperSave},
			    #{awayKeeperSave},
			    #{homeFreeKick},
			    #{awayFreeKick},
			    #{homeOffside},
			    #{awayOffside},
			    #{homeFoul},
			    #{awayFoul},
			    #{homeYellowCard},
			    #{awayYellowCard},
			    #{homeRedCard},
			    #{awayRedCard},
			    #{homeSlowIn},
			    #{awaySlowIn},
			    #{homeBoxTouch},
			    #{awayBoxTouch},
			    #{homePassCount},
			    #{awayPassCount},
			    #{homeFinalThirdPassCount},
			    #{awayFinalThirdPassCount},
			    #{homeCrossCount},
			    #{awayCrossCount},
			    #{homeTackleCount},
			    #{awayTackleCount},
			    #{homeClearCount},
			    #{awayClearCount},
			    #{homeInterceptCount},
			    #{awayInterceptCount},
			    CAST(NULLIF(#{recordTime}, '') AS timestamptz),
			    #{weather},
			    #{temparature},
			    #{humid},
			    #{judgeMember},
			    #{homeManager},
			    #{awayManager},
			    #{homeFormation},
			    #{awayFormation},
			    #{studium},
			    #{capacity},
			    #{audience},
			    #{homeMaxGettingScorer},
			    #{awayMaxGettingScorer},
			    #{homeMaxGettingScorerGameSituation},
			    #{awayMaxGettingScorerGameSituation},
			    #{homeTeamHomeScore},
			    #{homeTeamHomeLost},
			    #{awayTeamHomeScore},
			    #{awayTeamHomeLost},
			    #{homeTeamAwayScore},
			    #{homeTeamAwayLost},
			    #{awayTeamAwayScore},
			    #{awayTeamAwayLost},
			    #{noticeFlg},
			    #{goalTime},
			    #{goalTeamMember},
			    #{judge},
			    #{homeTeamStyle},
			    #{awayTeamStyle},
			    #{probablity},
			    #{predictionScoreTime},
			    #{gameId},
			    #{matchId},
			 #{timeSortSeconds},
			 #{addManualFlg},
			    #{registerId},
			    CAST(#{registerTime} AS timestamptz),
			    #{updateId},
			    CAST(#{updateTime} AS timestamptz)
			)
			RETURNING seq
			""")
	@Options(useGeneratedKeys = true, keyProperty = "seq", keyColumn = "seq")
	int insert(DataEntity entity);

	@Select("""
			SELECT COUNT(*)
			FROM data
			WHERE data_category = #{dataCategory}
			  AND times = #{times}
			  AND home_team_name = #{homeTeamName}
			  AND away_team_name = #{awayTeamName}
			  AND match_id        = #{matchId}
			""")
	int findDataCount(DataEntity entity);

	@Select("""
			SELECT *
			FROM data
			""")
	List<DataEntity> getData();

	@Select("""
			WITH team_list AS (
			    SELECT home_team_name AS team
			    FROM data
			    WHERE times = '終了済'
			      AND data_category LIKE CONCAT(#{country}, '%')
			      AND data_category LIKE CONCAT('%', #{league}, '%')
			      AND (
			            #{match} IS NULL
			         OR #{match} = ''
			         -- ★ ラウンド番号 <= match までを対象にする
			         OR substring(data_category from 'ラウンド ([0-9]+)')::integer
			            <= CAST(#{match} AS integer)
			      )
			    UNION
			    SELECT away_team_name AS team
			    FROM data
			    WHERE times = '終了済'
			      AND data_category LIKE CONCAT(#{country}, '%')
			      AND data_category LIKE CONCAT('%', #{league}, '%')
			      AND (
			            #{match} IS NULL
			         OR #{match} = ''
			         OR substring(data_category from 'ラウンド ([0-9]+)')::integer
			            <= CAST(#{match} AS integer)
			      )
			),
			team_stats AS (
			    SELECT
			        t.team,
			        SUM(
			            CASE
			                WHEN d.home_team_name = t.team THEN
			                    CASE
			                        WHEN COALESCE(NULLIF(d.home_score, '')::integer, 0)
			                             > COALESCE(NULLIF(d.away_score, '')::integer, 0)
			                             THEN 3
			                        WHEN COALESCE(NULLIF(d.home_score, '')::integer, 0)
			                             = COALESCE(NULLIF(d.away_score, '')::integer, 0)
			                             THEN 1
			                        ELSE 0
			                    END
			                WHEN d.away_team_name = t.team THEN
			                    CASE
			                        WHEN COALESCE(NULLIF(d.away_score, '')::integer, 0)
			                             > COALESCE(NULLIF(d.home_score, '')::integer, 0)
			                             THEN 3
			                        WHEN COALESCE(NULLIF(d.away_score, '')::integer, 0)
			                             = COALESCE(NULLIF(d.home_score, '')::integer, 0)
			                             THEN 1
			                        ELSE 0
			                    END
			                ELSE 0
			            END
			        ) AS points,
			        SUM(
			            CASE
			                WHEN d.home_team_name = t.team
			                    THEN COALESCE(NULLIF(d.home_score, '')::integer, 0)
			                WHEN d.away_team_name = t.team
			                    THEN COALESCE(NULLIF(d.away_score, '')::integer, 0)
			                ELSE 0
			            END
			        ) AS gf,
			        SUM(
			            CASE
			                WHEN d.home_team_name = t.team
			                    THEN COALESCE(NULLIF(d.away_score, '')::integer, 0)
			                WHEN d.away_team_name = t.team
			                    THEN COALESCE(NULLIF(d.home_score, '')::integer, 0)
			                ELSE 0
			            END
			        ) AS ga,
			        SUM(
			            CASE
			                WHEN d.home_team_name = t.team OR d.away_team_name = t.team THEN 1
			                ELSE 0
			            END
			        ) AS played
			    FROM team_list t
			    LEFT JOIN data d
			      ON (d.home_team_name = t.team OR d.away_team_name = t.team)
			     AND d.times = '終了済'
			     AND d.data_category LIKE CONCAT(#{country}, '%')
			     AND d.data_category LIKE CONCAT('%', #{league}, '%')
			     AND (
			            #{match} IS NULL
			         OR #{match} = ''
			         -- ★ JOIN 側も同じく「match まで」の条件にする
			         OR substring(d.data_category from 'ラウンド ([0-9]+)')::integer
			            <= CAST(#{match} AS integer)
			        )
			    GROUP BY t.team
			)
			SELECT
			    team,
			    points,
			    gf,
			    ga,
			    played
			FROM team_stats
			ORDER BY
			    points DESC,
			    (gf - ga) DESC,
			    gf DESC
			""")
	List<TeamPoints> selectTeamPoints(
			@Param("country") String country,
			@Param("league") String league,
			@Param("match") String match);

}
