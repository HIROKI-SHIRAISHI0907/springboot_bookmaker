package dev.application.domain.repository.bm;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.main.service.DataCategoryDTO;
import dev.application.main.service.SeqKeyDTO;
import dev.common.entity.DataEntity;
@Mapper
public interface BookDataRepository {
	// ★static_data向けに変更。seq_keyはアプリ側で採番済みのためRETURNING seq / useGeneratedKeysは不要
	@Insert("""
			INSERT INTO static_data (
			    seq_key,
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
			    home_long_pass_count,
			    away_long_pass_count,
			    home_final_third_pass_count,
			    away_final_third_pass_count,
			    home_cross_count,
			    away_cross_count,
			    home_tackle_count,
			    away_tackle_count,
			    home_clear_count,
			    away_clear_count,
			    home_duel_count,
			    away_duel_count,
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
			    location,
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
			    game_link,
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
			    #{seqKey},
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
			    #{homeLongPassCount},
			    #{awayLongPassCount},
			    #{homeFinalThirdPassCount},
			    #{awayFinalThirdPassCount},
			    #{homeCrossCount},
			    #{awayCrossCount},
			    #{homeTackleCount},
			    #{awayTackleCount},
			    #{homeClearCount},
			    #{awayClearCount},
			    #{homeDuelCount},
			    #{awayDuelCount},
			    #{homeInterceptCount},
			    #{awayInterceptCount},
			    CAST(NULLIF(#{recordTime}, '') AS timestamp),
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
			    #{location},
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
			    #{gameLink},
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
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(DataEntity entity);
	// ★static_data向けに変更
	@Select("""
			SELECT COUNT(*)
			FROM static_data
			WHERE data_category = #{dataCategory}
			  AND times = #{times}
			  AND home_team_name = #{homeTeamName}
			  AND away_team_name = #{awayTeamName}
			  AND match_id        = #{matchId}
			""")
	int findDataCount(DataEntity entity);
	@Select("""
			SELECT *
			FROM static_data
			""")
	List<DataEntity> getData();
	@Select("""
			SELECT
			    seq_key AS seqKey,
			    condition_result_data_seq_id AS conditionResultDataSeqId,
			    data_category AS dataCategory,
			    times AS times,
			    home_rank AS homeRank,
			    home_team_name AS homeTeamName,
			    home_score AS homeScore,
			    away_rank AS awayRank,
			    away_team_name AS awayTeamName,
			    away_score AS awayScore,
			    home_exp AS homeExp,
			    away_exp AS awayExp,
			    home_in_goal_exp AS homeInGoalExp,
			    away_in_goal_exp AS awayInGoalExp,
			    home_donation AS homeDonation,
			    away_donation AS awayDonation,
			    home_shoot_all AS homeShootAll,
			    away_shoot_all AS awayShootAll,
			    home_shoot_in AS homeShootIn,
			    away_shoot_in AS awayShootIn,
			    home_shoot_out AS homeShootOut,
			    away_shoot_out AS awayShootOut,
			    home_block_shoot AS homeBlockShoot,
			    away_block_shoot AS awayBlockShoot,
			    home_big_chance AS homeBigChance,
			    away_big_chance AS awayBigChance,
			    home_corner AS homeCorner,
			    away_corner AS awayCorner,
			    home_box_shoot_in AS homeBoxShootIn,
			    away_box_shoot_in AS awayBoxShootIn,
			    home_box_shoot_out AS homeBoxShootOut,
			    away_box_shoot_out AS awayBoxShootOut,
			    home_goal_post AS homeGoalPost,
			    away_goal_post AS awayGoalPost,
			    home_goal_head AS homeGoalHead,
			    away_goal_head AS awayGoalHead,
			    home_keeper_save AS homeKeeperSave,
			    away_keeper_save AS awayKeeperSave,
			    home_free_kick AS homeFreeKick,
			    away_free_kick AS awayFreeKick,
			    home_offside AS homeOffside,
			    away_offside AS awayOffside,
			    home_foul AS homeFoul,
			    away_foul AS awayFoul,
			    home_yellow_card AS homeYellowCard,
			    away_yellow_card AS awayYellowCard,
			    home_red_card AS homeRedCard,
			    away_red_card AS awayRedCard,
			    home_slow_in AS homeSlowIn,
			    away_slow_in AS awaySlowIn,
			    home_box_touch AS homeBoxTouch,
			    away_box_touch AS awayBoxTouch,
			    home_pass_count AS homePassCount,
			    away_pass_count AS awayPassCount,
			    home_long_pass_count AS homeLongPassCount,
			    away_long_pass_count AS awayLongPassCount,
			    home_final_third_pass_count AS homeFinalThirdPassCount,
			    away_final_third_pass_count AS awayFinalThirdPassCount,
			    home_cross_count AS homeCrossCount,
			    away_cross_count AS awayCrossCount,
			    home_tackle_count AS homeTackleCount,
			    away_tackle_count AS awayTackleCount,
			    home_clear_count AS homeClearCount,
			    away_clear_count AS awayClearCount,
			    home_duel_count AS homeDuelCount,
			    away_duel_count AS awayDuelCount,
			    home_intercept_count AS homeInterceptCount,
			    away_intercept_count AS awayInterceptCount,
			    record_time AS recordTime,
			    weather AS weather,
			    temparature AS temparature,
			    humid AS humid,
			    judge_member AS judgeMember,
			    home_manager AS homeManager,
			    away_manager AS awayManager,
			    home_formation AS homeFormation,
			    away_formation AS awayFormation,
			    studium AS studium,
			    capacity AS capacity,
			    audience AS audience,
			    location AS location,
			    home_max_getting_scorer AS homeMaxGettingScorer,
			    away_max_getting_scorer AS awayMaxGettingScorer,
			    home_max_getting_scorer_game_situation AS homeMaxGettingScorerGameSituation,
			    away_max_getting_scorer_game_situation AS awayMaxGettingScorerGameSituation,
			    home_team_home_score AS homeTeamHomeScore,
			    home_team_home_lost AS homeTeamHomeLost,
			    away_team_home_score AS awayTeamHomeScore,
			    away_team_home_lost AS awayTeamHomeLost,
			    home_team_away_score AS homeTeamAwayScore,
			    home_team_away_lost AS homeTeamAwayLost,
			    away_team_away_score AS awayTeamAwayScore,
			    away_team_away_lost AS awayTeamAwayLost,
			    notice_flg AS noticeFlg,
			    game_link AS gameLink,
			    goal_time AS goalTime,
			    goal_team_member AS goalTeamMember,
			    judge AS judge,
			    home_team_style AS homeTeamStyle,
			    away_team_style AS awayTeamStyle,
			    probablity AS probablity,
			    prediction_score_time AS predictionScoreTime,
			    game_id AS gameId,
			    match_id AS matchId,
			    time_sort_seconds AS timeSortSeconds,
			    add_manual_flg AS addManualFlg
			FROM static_data d
			WHERE d.add_manual_flg = '1'
			  AND (d.times = '終了済' OR d.times LIKE 'ペナルティ%')
			  AND d.match_id IS NOT NULL
			  AND d.match_id <> ''
			  AND (
			      SELECT COUNT(*)
			      FROM static_data d2
			      WHERE d2.match_id = d.match_id
			  ) = 1
			""")
	List<DataEntity> getFinData();
	@Select("""
			SELECT
				COUNT(*)
			FROM static_data
				WHERE data_category = #{dataCategory}
				AND home_team_name = #{homeTeamName}
				AND away_team_name = #{awayTeamName}
				AND (
					(#{matchId} IS NOT NULL AND match_id = #{matchId})
				OR
					(#{matchId} IS NULL AND match_id IS NULL)
				)
			""")
	int getAnalyzeManualRestrictCount(
			@Param("dataCategory") String dataCategory,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("matchId") String matchId);
	/**
	 * data_category + home_team_name + away_team_name で
	 * seq降順の最新2件を取得
	 */
	@Select("""
			SELECT
				seq_key AS seqKey,
			    condition_result_data_seq_id AS conditionResultDataSeqId,
			    data_category AS dataCategory,
			    times AS times,
			    home_rank AS homeRank,
			    home_team_name AS homeTeamName,
			    home_score AS homeScore,
			    away_rank AS awayRank,
			    away_team_name AS awayTeamName,
			    away_score AS awayScore,
			    home_exp AS homeExp,
			    away_exp AS awayExp,
			    home_in_goal_exp AS homeInGoalExp,
			    away_in_goal_exp AS awayInGoalExp,
			    home_donation AS homeDonation,
			    away_donation AS awayDonation,
			    home_shoot_all AS homeShootAll,
			    away_shoot_all AS awayShootAll,
			    home_shoot_in AS homeShootIn,
			    away_shoot_in AS awayShootIn,
			    home_shoot_out AS homeShootOut,
			    away_shoot_out AS awayShootOut,
			    home_block_shoot AS homeBlockShoot,
			    away_block_shoot AS awayBlockShoot,
			    home_big_chance AS homeBigChance,
			    away_big_chance AS awayBigChance,
			    home_corner AS homeCorner,
			    away_corner AS awayCorner,
			    home_box_shoot_in AS homeBoxShootIn,
			    away_box_shoot_in AS awayBoxShootIn,
			    home_box_shoot_out AS homeBoxShootOut,
			    away_box_shoot_out AS awayBoxShootOut,
			    home_goal_post AS homeGoalPost,
			    away_goal_post AS awayGoalPost,
			    home_goal_head AS homeGoalHead,
			    away_goal_head AS awayGoalHead,
			    home_keeper_save AS homeKeeperSave,
			    away_keeper_save AS awayKeeperSave,
			    home_free_kick AS homeFreeKick,
			    away_free_kick AS awayFreeKick,
			    home_offside AS homeOffside,
			    away_offside AS awayOffside,
			    home_foul AS homeFoul,
			    away_foul AS awayFoul,
			    home_yellow_card AS homeYellowCard,
			    away_yellow_card AS awayYellowCard,
			    home_red_card AS homeRedCard,
			    away_red_card AS awayRedCard,
			    home_slow_in AS homeSlowIn,
			    away_slow_in AS awaySlowIn,
			    home_box_touch AS homeBoxTouch,
			    away_box_touch AS awayBoxTouch,
			    home_pass_count AS homePassCount,
			    away_pass_count AS awayPassCount,
			    home_long_pass_count AS homeLongPassCount,
			    away_long_pass_count AS awayLongPassCount,
			    home_final_third_pass_count AS homeFinalThirdPassCount,
			    away_final_third_pass_count AS awayFinalThirdPassCount,
			    home_cross_count AS homeCrossCount,
			    away_cross_count AS awayCrossCount,
			    home_tackle_count AS homeTackleCount,
			    away_tackle_count AS awayTackleCount,
			    home_clear_count AS homeClearCount,
			    away_clear_count AS awayClearCount,
			    home_duel_count AS homeDuelCount,
			    away_duel_count AS awayDuelCount,
			    home_intercept_count AS homeInterceptCount,
			    away_intercept_count AS awayInterceptCount,
			    record_time AS recordTime,
			    weather AS weather,
			    temparature AS temparature,
			    humid AS humid,
			    judge_member AS judgeMember,
			    home_manager AS homeManager,
			    away_manager AS awayManager,
			    home_formation AS homeFormation,
			    away_formation AS awayFormation,
			    studium AS studium,
			    capacity AS capacity,
			    audience AS audience,
			    location AS location,
			    home_max_getting_scorer AS homeMaxGettingScorer,
			    away_max_getting_scorer AS awayMaxGettingScorer,
			    home_max_getting_scorer_game_situation AS homeMaxGettingScorerGameSituation,
			    away_max_getting_scorer_game_situation AS awayMaxGettingScorerGameSituation,
			    home_team_home_score AS homeTeamHomeScore,
			    home_team_home_lost AS homeTeamHomeLost,
			    away_team_home_score AS awayTeamHomeScore,
			    away_team_home_lost AS awayTeamHomeLost,
			    home_team_away_score AS homeTeamAwayScore,
			    home_team_away_lost AS homeTeamAwayLost,
			    away_team_away_score AS awayTeamAwayScore,
			    away_team_away_lost AS awayTeamAwayLost,
			    notice_flg AS noticeFlg,
			    game_link AS gameLink,
			    goal_time AS goalTime,
			    goal_team_member AS goalTeamMember,
			    judge AS judge,
			    home_team_style AS homeTeamStyle,
			    away_team_style AS awayTeamStyle,
			    probablity AS probablity,
			    prediction_score_time AS predictionScoreTime,
			    game_id AS gameId,
			    match_id AS matchId,
			    time_sort_seconds AS timeSortSeconds,
			    add_manual_flg AS addManualFlg
			FROM static_data
			WHERE data_category = #{dataCategory}
				AND home_team_name = #{homeTeamName}
				AND away_team_name = #{awayTeamName}
			ORDER BY seq_key DESC
			LIMIT 2
			""")
	List<DataEntity> findLatestTwoByTeams(
			@Param("dataCategory") String dataCategory,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);
	// ★static_data向けに変更。カンマ抜け修正 + 連番部分を数値としてORDER BY（"9"が"10"より新しいと誤判定するのを防止）
	// 変更後：終了済み(times='終了済' / 'ペナルティ%')の過去カードと、
	// 念のため直近6時間より前の古いレコードを対象から除外し、
	// 「現在進行中の対戦」だけを見るようにする
	@Select("""
	        SELECT
	            seq_key AS seqKey,
	            match_id AS matchId,
	            times
	        FROM static_data
	            WHERE home_team_name = #{homeTeamName}
	            AND away_team_name = #{awayTeamName}
	            AND BTRIM(home_team_name) <> ''
	            AND BTRIM(away_team_name) <> ''
	            AND (times IS NULL OR (times <> '終了済' AND times NOT LIKE 'ペナルティ%'))
	            AND register_time >= CURRENT_TIMESTAMP - INTERVAL '6 hours'
	        ORDER BY register_time DESC,
	                 NULLIF(SUBSTRING(seq_key FROM '([0-9]+)$'), '')::BIGINT DESC NULLS LAST
	        """)
	List<SeqKeyDTO> findMatchId(
	        @Param("homeTeamName") String homeTeamName,
	        @Param("awayTeamName") String awayTeamName);
	// ★static_data向けに変更。カンマ抜け修正 + 連番部分を数値としてORDER BY
	@Select("""
			SELECT
			    seq_key AS seqKey,
			    match_id AS matchId
			FROM static_data
			WHERE match_id = #{matchId}
			   OR seq_key LIKE CONCAT(#{matchId}, '-%')
			ORDER BY NULLIF(SUBSTRING(seq_key FROM '([0-9]+)$'), '')::BIGINT DESC NULLS LAST
			LIMIT 1
			""")
	SeqKeyDTO findSeqKeyByMatchId(
			@Param("matchId") String matchId);
	@Select("""
			SELECT
				data_category AS dataCategory,
				times
			FROM static_data
			WHERE home_team_name = #{homeTeamName}
			  AND away_team_name = #{awayTeamName}
			  AND (match_id = #{matchId,jdbcType=VARCHAR}
			       OR register_time >= CURRENT_TIMESTAMP - INTERVAL '6 hours')
			  AND data_category IS NOT NULL
			  AND data_category !~* '^\\s*XXX\\s*:'
			ORDER BY register_time DESC
			""")
	List<DataCategoryDTO> findDataCategoryForMatch(
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("matchId") String matchId);
	// ★新規追加：乱数base（match_idなしの初回キー）の重複チェック用
	@Select("""
			SELECT COUNT(*)
			FROM static_data
			WHERE seq_key LIKE CONCAT(#{prefix}, '-%')
			""")
	int existsSeqKeyPrefix(@Param("prefix") String prefix);

	@Update("""
	        UPDATE static_data
	        SET seq_key = #{newSeqKey},
	            match_id = #{matchId}
	        WHERE seq_key = #{oldSeqKey}
	        """)
	int updateSeqKey(@Param("oldSeqKey") String oldSeqKey,
	                  @Param("newSeqKey") String newSeqKey,
	                  @Param("matchId") String matchId);

	@Select("""
			SELECT
				data_category AS dataCategory,
				times
			FROM static_data
				WHERE home_team_name = #{homeTeamName}
				AND away_team_name = #{awayTeamName}
			ORDER BY register_time DESC;
			""")
	List<DataCategoryDTO> findDataCategory(
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	@Update("""
			UPDATE static_data
			SET data_category = #{dataCategory}
			WHERE home_team_name = #{homeTeamName}
			  AND away_team_name = #{awayTeamName}
			  AND (match_id = #{matchId,jdbcType=VARCHAR}
			       OR register_time >= CURRENT_TIMESTAMP - INTERVAL '6 hours')
			  AND data_category IS DISTINCT FROM #{dataCategory}
			""")
	int updateDataCategoryForMatch(
			@Param("dataCategory") String dataCategory,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("matchId") String matchId);

	@Update("""
			UPDATE static_data
			SET data_category = #{dataCategory}
				WHERE home_team_name = #{homeTeamName}
				AND away_team_name = #{awayTeamName}
			""")
	int updateByDataCategory(
			@Param("dataCategory") String dataCategory,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);
}