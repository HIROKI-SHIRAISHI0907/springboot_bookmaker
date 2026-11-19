package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m029.RealDataProcessEntity;

/**
 * real_data_process Mapper（BM_M029 リアルタイム差分）。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>{@link #findSeqByMatchId}: 既存行の seq を取得する（上書き時に番号を消費しないため）。</li>
 *   <li>{@link #upsertByMatchId}: match_id をキーに UPSERT する（1試合1行。最新の差分で上書き）。</li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT）。</li>
 *   <li><b>メタデータは NOT NULL</b>。Entity に値がなければ ID='SYSTEM'、日時=NOW()。更新時は seq・register_* を変えない。</li>
 *   <li><b>同時実行で同じ試合を2つの処理が新規として採番した場合</b>: 後の INSERT は更新になり、後の番号は欠番になる。</li>
 * </ul>
 */
@Mapper
public interface RealDataProcessRepository {

	/**
	 * 既存行の seq（なければ null）。
	 */
	@Select("SELECT seq FROM real_data_process WHERE match_id = #{matchId}")
	String findSeqByMatchId(@Param("matchId") String matchId);

	/**
	 * match_id をキーに UPSERT する。
	 *
	 * @param entity 登録対象（seq・season 設定済み）
	 * @return 処理件数（INSERT・UPDATE とも 1）
	 */
	@Insert({
			"INSERT INTO real_data_process (",
			"seq, season, country, league, match_id, ",
			"data_category, game_id, game_link, condition_result_data_seq_id, home_team_name, ",
			"away_team_name, home_rank, away_rank, has_previous, prev_times, ",
			"times, prev_record_time, record_time, home_current_score, away_current_score, ",
			"home_score, home_exp, home_in_goal_exp, home_donation, home_shoot_all, ",
			"home_shoot_in, home_shoot_out, home_block_shoot, home_big_chance, home_corner, ",
			"home_box_shoot_in, home_box_shoot_out, home_goal_post, home_goal_head, home_keeper_save, ",
			"home_free_kick, home_offside, home_foul, home_yellow_card, home_red_card, ",
			"home_slow_in, home_box_touch, home_clear_count, home_duel_count, home_intercept_count, ",
			"home_pass_count_success, home_pass_count_try, home_pass_count_rate, home_long_pass_count_success, home_long_pass_count_try, ",
			"home_long_pass_count_rate, home_final_third_pass_count_success, home_final_third_pass_count_try, home_final_third_pass_count_rate, home_cross_count_success, ",
			"home_cross_count_try, home_cross_count_rate, home_tackle_count_success, home_tackle_count_try, home_tackle_count_rate, ",
			"away_score, away_exp, away_in_goal_exp, away_donation, away_shoot_all, ",
			"away_shoot_in, away_shoot_out, away_block_shoot, away_big_chance, away_corner, ",
			"away_box_shoot_in, away_box_shoot_out, away_goal_post, away_goal_head, away_keeper_save, ",
			"away_free_kick, away_offside, away_foul, away_yellow_card, away_red_card, ",
			"away_slow_in, away_box_touch, away_clear_count, away_duel_count, away_intercept_count, ",
			"away_pass_count_success, away_pass_count_try, away_pass_count_rate, away_long_pass_count_success, away_long_pass_count_try, ",
			"away_long_pass_count_rate, away_final_third_pass_count_success, away_final_third_pass_count_try, away_final_third_pass_count_rate, away_cross_count_success, ",
			"away_cross_count_try, away_cross_count_rate, away_tackle_count_success, away_tackle_count_try, away_tackle_count_rate, ",
			"probablity_diff, probablity, prediction_score_time, weather, temperature, ",
			"humid, judge_member, home_manager, away_manager, home_formation, ",
			"away_formation, studium, capacity, audience, location, ",
			"home_max_getting_scorer, away_max_getting_scorer, home_max_getting_scorer_game_situation, away_max_getting_scorer_game_situation, home_team_home_score, ",
			"home_team_home_lost, away_team_home_score, away_team_home_lost, home_team_away_score, home_team_away_lost, ",
			"away_team_away_score, away_team_away_lost, notice_flg, goal_time, goal_team_member, ",
			"judge, home_team_style, away_team_style, ",
			"register_id, register_time, update_id, update_time",
			") VALUES (",
			"#{seq}, #{season}, #{country}, #{league}, #{matchId}, ",
			"#{dataCategory}, #{gameId}, #{gameLink}, #{conditionResultDataSeqId}, #{homeTeamName}, ",
			"#{awayTeamName}, #{homeRank}, #{awayRank}, #{hasPrevious}, #{prevTimes}, ",
			"#{times}, #{prevRecordTime}, #{recordTime}, #{homeCurrentScore}, #{awayCurrentScore}, ",
			"#{homeScore}, #{homeExp}, #{homeInGoalExp}, #{homeDonation}, #{homeShootAll}, ",
			"#{homeShootIn}, #{homeShootOut}, #{homeBlockShoot}, #{homeBigChance}, #{homeCorner}, ",
			"#{homeBoxShootIn}, #{homeBoxShootOut}, #{homeGoalPost}, #{homeGoalHead}, #{homeKeeperSave}, ",
			"#{homeFreeKick}, #{homeOffside}, #{homeFoul}, #{homeYellowCard}, #{homeRedCard}, ",
			"#{homeSlowIn}, #{homeBoxTouch}, #{homeClearCount}, #{homeDuelCount}, #{homeInterceptCount}, ",
			"#{homePassCountSuccess}, #{homePassCountTry}, #{homePassCountRate}, #{homeLongPassCountSuccess}, #{homeLongPassCountTry}, ",
			"#{homeLongPassCountRate}, #{homeFinalThirdPassCountSuccess}, #{homeFinalThirdPassCountTry}, #{homeFinalThirdPassCountRate}, #{homeCrossCountSuccess}, ",
			"#{homeCrossCountTry}, #{homeCrossCountRate}, #{homeTackleCountSuccess}, #{homeTackleCountTry}, #{homeTackleCountRate}, ",
			"#{awayScore}, #{awayExp}, #{awayInGoalExp}, #{awayDonation}, #{awayShootAll}, ",
			"#{awayShootIn}, #{awayShootOut}, #{awayBlockShoot}, #{awayBigChance}, #{awayCorner}, ",
			"#{awayBoxShootIn}, #{awayBoxShootOut}, #{awayGoalPost}, #{awayGoalHead}, #{awayKeeperSave}, ",
			"#{awayFreeKick}, #{awayOffside}, #{awayFoul}, #{awayYellowCard}, #{awayRedCard}, ",
			"#{awaySlowIn}, #{awayBoxTouch}, #{awayClearCount}, #{awayDuelCount}, #{awayInterceptCount}, ",
			"#{awayPassCountSuccess}, #{awayPassCountTry}, #{awayPassCountRate}, #{awayLongPassCountSuccess}, #{awayLongPassCountTry}, ",
			"#{awayLongPassCountRate}, #{awayFinalThirdPassCountSuccess}, #{awayFinalThirdPassCountTry}, #{awayFinalThirdPassCountRate}, #{awayCrossCountSuccess}, ",
			"#{awayCrossCountTry}, #{awayCrossCountRate}, #{awayTackleCountSuccess}, #{awayTackleCountTry}, #{awayTackleCountRate}, ",
			"#{probablityDiff}, #{probablity}, #{predictionScoreTime}, #{weather}, #{temperature}, ",
			"#{humid}, #{judgeMember}, #{homeManager}, #{awayManager}, #{homeFormation}, ",
			"#{awayFormation}, #{studium}, #{capacity}, #{audience}, #{location}, ",
			"#{homeMaxGettingScorer}, #{awayMaxGettingScorer}, #{homeMaxGettingScorerGameSituation}, #{awayMaxGettingScorerGameSituation}, #{homeTeamHomeScore}, ",
			"#{homeTeamHomeLost}, #{awayTeamHomeScore}, #{awayTeamHomeLost}, #{homeTeamAwayScore}, #{homeTeamAwayLost}, ",
			"#{awayTeamAwayScore}, #{awayTeamAwayLost}, #{noticeFlg}, #{goalTime}, #{goalTeamMember}, ",
			"#{judge}, #{homeTeamStyle}, #{awayTeamStyle}, ",
			"COALESCE(#{registerId}, 'SYSTEM'), COALESCE(CAST(#{registerTime} AS timestamptz), NOW()), ",
			"COALESCE(#{updateId}, 'SYSTEM'), COALESCE(CAST(#{updateTime} AS timestamptz), NOW())",
			") ON CONFLICT (match_id) DO UPDATE SET ",
			// seq・match_id・register_* は変えない
			"season = EXCLUDED.season, country = EXCLUDED.country, league = EXCLUDED.league, ",
			"data_category = EXCLUDED.data_category, game_id = EXCLUDED.game_id, game_link = EXCLUDED.game_link, ",
			"condition_result_data_seq_id = EXCLUDED.condition_result_data_seq_id, home_team_name = EXCLUDED.home_team_name, away_team_name = EXCLUDED.away_team_name, ",
			"home_rank = EXCLUDED.home_rank, away_rank = EXCLUDED.away_rank, has_previous = EXCLUDED.has_previous, ",
			"prev_times = EXCLUDED.prev_times, times = EXCLUDED.times, prev_record_time = EXCLUDED.prev_record_time, ",
			"record_time = EXCLUDED.record_time, home_current_score = EXCLUDED.home_current_score, away_current_score = EXCLUDED.away_current_score, ",
			"home_score = EXCLUDED.home_score, home_exp = EXCLUDED.home_exp, home_in_goal_exp = EXCLUDED.home_in_goal_exp, ",
			"home_donation = EXCLUDED.home_donation, home_shoot_all = EXCLUDED.home_shoot_all, home_shoot_in = EXCLUDED.home_shoot_in, ",
			"home_shoot_out = EXCLUDED.home_shoot_out, home_block_shoot = EXCLUDED.home_block_shoot, home_big_chance = EXCLUDED.home_big_chance, ",
			"home_corner = EXCLUDED.home_corner, home_box_shoot_in = EXCLUDED.home_box_shoot_in, home_box_shoot_out = EXCLUDED.home_box_shoot_out, ",
			"home_goal_post = EXCLUDED.home_goal_post, home_goal_head = EXCLUDED.home_goal_head, home_keeper_save = EXCLUDED.home_keeper_save, ",
			"home_free_kick = EXCLUDED.home_free_kick, home_offside = EXCLUDED.home_offside, home_foul = EXCLUDED.home_foul, ",
			"home_yellow_card = EXCLUDED.home_yellow_card, home_red_card = EXCLUDED.home_red_card, home_slow_in = EXCLUDED.home_slow_in, ",
			"home_box_touch = EXCLUDED.home_box_touch, home_clear_count = EXCLUDED.home_clear_count, home_duel_count = EXCLUDED.home_duel_count, ",
			"home_intercept_count = EXCLUDED.home_intercept_count, home_pass_count_success = EXCLUDED.home_pass_count_success, home_pass_count_try = EXCLUDED.home_pass_count_try, ",
			"home_pass_count_rate = EXCLUDED.home_pass_count_rate, home_long_pass_count_success = EXCLUDED.home_long_pass_count_success, home_long_pass_count_try = EXCLUDED.home_long_pass_count_try, ",
			"home_long_pass_count_rate = EXCLUDED.home_long_pass_count_rate, home_final_third_pass_count_success = EXCLUDED.home_final_third_pass_count_success, home_final_third_pass_count_try = EXCLUDED.home_final_third_pass_count_try, ",
			"home_final_third_pass_count_rate = EXCLUDED.home_final_third_pass_count_rate, home_cross_count_success = EXCLUDED.home_cross_count_success, home_cross_count_try = EXCLUDED.home_cross_count_try, ",
			"home_cross_count_rate = EXCLUDED.home_cross_count_rate, home_tackle_count_success = EXCLUDED.home_tackle_count_success, home_tackle_count_try = EXCLUDED.home_tackle_count_try, ",
			"home_tackle_count_rate = EXCLUDED.home_tackle_count_rate, away_score = EXCLUDED.away_score, away_exp = EXCLUDED.away_exp, ",
			"away_in_goal_exp = EXCLUDED.away_in_goal_exp, away_donation = EXCLUDED.away_donation, away_shoot_all = EXCLUDED.away_shoot_all, ",
			"away_shoot_in = EXCLUDED.away_shoot_in, away_shoot_out = EXCLUDED.away_shoot_out, away_block_shoot = EXCLUDED.away_block_shoot, ",
			"away_big_chance = EXCLUDED.away_big_chance, away_corner = EXCLUDED.away_corner, away_box_shoot_in = EXCLUDED.away_box_shoot_in, ",
			"away_box_shoot_out = EXCLUDED.away_box_shoot_out, away_goal_post = EXCLUDED.away_goal_post, away_goal_head = EXCLUDED.away_goal_head, ",
			"away_keeper_save = EXCLUDED.away_keeper_save, away_free_kick = EXCLUDED.away_free_kick, away_offside = EXCLUDED.away_offside, ",
			"away_foul = EXCLUDED.away_foul, away_yellow_card = EXCLUDED.away_yellow_card, away_red_card = EXCLUDED.away_red_card, ",
			"away_slow_in = EXCLUDED.away_slow_in, away_box_touch = EXCLUDED.away_box_touch, away_clear_count = EXCLUDED.away_clear_count, ",
			"away_duel_count = EXCLUDED.away_duel_count, away_intercept_count = EXCLUDED.away_intercept_count, away_pass_count_success = EXCLUDED.away_pass_count_success, ",
			"away_pass_count_try = EXCLUDED.away_pass_count_try, away_pass_count_rate = EXCLUDED.away_pass_count_rate, away_long_pass_count_success = EXCLUDED.away_long_pass_count_success, ",
			"away_long_pass_count_try = EXCLUDED.away_long_pass_count_try, away_long_pass_count_rate = EXCLUDED.away_long_pass_count_rate, away_final_third_pass_count_success = EXCLUDED.away_final_third_pass_count_success, ",
			"away_final_third_pass_count_try = EXCLUDED.away_final_third_pass_count_try, away_final_third_pass_count_rate = EXCLUDED.away_final_third_pass_count_rate, away_cross_count_success = EXCLUDED.away_cross_count_success, ",
			"away_cross_count_try = EXCLUDED.away_cross_count_try, away_cross_count_rate = EXCLUDED.away_cross_count_rate, away_tackle_count_success = EXCLUDED.away_tackle_count_success, ",
			"away_tackle_count_try = EXCLUDED.away_tackle_count_try, away_tackle_count_rate = EXCLUDED.away_tackle_count_rate, probablity_diff = EXCLUDED.probablity_diff, ",
			"probablity = EXCLUDED.probablity, prediction_score_time = EXCLUDED.prediction_score_time, weather = EXCLUDED.weather, ",
			"temperature = EXCLUDED.temperature, humid = EXCLUDED.humid, judge_member = EXCLUDED.judge_member, ",
			"home_manager = EXCLUDED.home_manager, away_manager = EXCLUDED.away_manager, home_formation = EXCLUDED.home_formation, ",
			"away_formation = EXCLUDED.away_formation, studium = EXCLUDED.studium, capacity = EXCLUDED.capacity, ",
			"audience = EXCLUDED.audience, location = EXCLUDED.location, home_max_getting_scorer = EXCLUDED.home_max_getting_scorer, ",
			"away_max_getting_scorer = EXCLUDED.away_max_getting_scorer, home_max_getting_scorer_game_situation = EXCLUDED.home_max_getting_scorer_game_situation, away_max_getting_scorer_game_situation = EXCLUDED.away_max_getting_scorer_game_situation, ",
			"home_team_home_score = EXCLUDED.home_team_home_score, home_team_home_lost = EXCLUDED.home_team_home_lost, away_team_home_score = EXCLUDED.away_team_home_score, ",
			"away_team_home_lost = EXCLUDED.away_team_home_lost, home_team_away_score = EXCLUDED.home_team_away_score, home_team_away_lost = EXCLUDED.home_team_away_lost, ",
			"away_team_away_score = EXCLUDED.away_team_away_score, away_team_away_lost = EXCLUDED.away_team_away_lost, notice_flg = EXCLUDED.notice_flg, ",
			"goal_time = EXCLUDED.goal_time, goal_team_member = EXCLUDED.goal_team_member, judge = EXCLUDED.judge, ",
			"home_team_style = EXCLUDED.home_team_style, away_team_style = EXCLUDED.away_team_style, ",
			"update_id = EXCLUDED.update_id, update_time = EXCLUDED.update_time"
	})
	int upsertByMatchId(RealDataProcessEntity entity);
}