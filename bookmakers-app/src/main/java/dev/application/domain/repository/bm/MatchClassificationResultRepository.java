package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m018.MatchClassificationResultEntity;


/**
 * classify_result_data Mapper（BM_M019 分類別の試合スナップショット）。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>{@link #upsert}: 1行（1試合の1時点）を UPSERT する。一意キー
 *       (season, country, league, home_team_name, away_team_name, snapshot_type, goal_no) が既にあれば上書き。</li>
 *   <li>{@link #findSeqByMatchKey}: 試合の既存行の時点と seq を取得する（再処理で番号を消費しないため）。</li>
 *   <li>{@link #deleteBySeq}: 今回の計算で無くなった時点の行を消す（ゴール取り消しなど）。</li>
 * </ul>
 * <p>
 * 旧 MatchClassificationResultRepository（insertBatch）と MatchClassificationResultCountRepository を置き換える。
 * BM_M020 の件数はビュー classify_result_data_detail から読むこと。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT）。</li>
 *   <li><b>メタデータは NOT NULL</b>。Entity に値がなければ ID='SYSTEM'、日時=NOW()。更新時は register_* を変えない。</li>
 *   <li><b>同時実行で同じ試合を2つの処理が新規として採番した場合</b>: 後の INSERT は更新になり、後の番号は欠番になる。</li>
 * </ul>
 */
@Mapper
public interface MatchClassificationResultRepository {

	/**
	 * 1試合分の既存行の時点（snapshot_type, goal_no）と seq を取得する。
	 *
	 * @return 既存行（snapshotType・goalNo・seq のみ設定。なければ空）
	 */
	@Select({
			"SELECT snapshot_type, goal_no, seq FROM classify_result_data ",
			"WHERE season = #{season} AND country = #{country} AND league = #{league} ",
			"AND home_team_name = #{homeTeamName} AND away_team_name = #{awayTeamName}"
	})
	@Results(id = "classifySnapshotSeq", value = {
			@Result(column = "snapshot_type", property = "snapshotType"),
			@Result(column = "goal_no", property = "goalNo"),
			@Result(column = "seq", property = "seq")
	})
	List<MatchClassificationResultEntity> findSeqByMatchKey(
			@Param("season") String season,
			@Param("country") String country,
			@Param("league") String league,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	/**
	 * seq で1行削除する。
	 *
	 * @return 削除件数
	 */
	@Delete("DELETE FROM classify_result_data WHERE seq = #{seq}")
	int deleteBySeq(@Param("seq") String seq);

	/**
	 * 1行を UPSERT する。
	 *
	 * @param entity 登録対象
	 * @return 処理件数（INSERT・UPDATE とも 1）
	 */
	@Insert({
			"INSERT INTO classify_result_data (",
			"seq, season, country, league, classify_mode, snapshot_type, ",
			"goal_no, data_seq, match_id, data_category, times, home_rank, ",
			"home_team_name, home_score, away_rank, away_team_name, away_score, home_exp, ",
			"away_exp, home_in_goal_exp, away_in_goal_exp, home_donation, away_donation, home_shoot_all, ",
			"away_shoot_all, home_shoot_in, away_shoot_in, home_shoot_out, away_shoot_out, home_block_shoot, ",
			"away_block_shoot, home_big_chance, away_big_chance, home_corner, away_corner, home_box_shoot_in, ",
			"away_box_shoot_in, home_box_shoot_out, away_box_shoot_out, home_goal_post, away_goal_post, home_goal_head, ",
			"away_goal_head, home_keeper_save, away_keeper_save, home_free_kick, away_free_kick, home_offside, ",
			"away_offside, home_foul, away_foul, home_yellow_card, away_yellow_card, home_red_card, ",
			"away_red_card, home_slow_in, away_slow_in, home_box_touch, away_box_touch, home_pass_count, ",
			"away_pass_count, home_long_pass_count, away_long_pass_count, home_final_third_pass_count, away_final_third_pass_count, home_cross_count, ",
			"away_cross_count, home_tackle_count, away_tackle_count, home_clear_count, away_clear_count, home_duel_count, ",
			"away_duel_count, home_intercept_count, away_intercept_count, record_time, weather, temperature, ",
			"humid, judge_member, home_manager, away_manager, home_formation, away_formation, ",
			"studium, capacity, audience, home_max_getting_scorer, away_max_getting_scorer, home_max_getting_scorer_game_situation, ",
			"away_max_getting_scorer_game_situation, home_team_home_score, home_team_home_lost, away_team_home_score, away_team_home_lost, home_team_away_score, ",
			"home_team_away_lost, away_team_away_score, away_team_away_lost, notice_flg, goal_time, goal_team_member, ",
			"judge, home_team_style, away_team_style, probablity, prediction_score_time, ",
			"register_id, register_time, update_id, update_time",
			") VALUES (",
			"#{seq}, #{season}, #{country}, #{league}, #{classifyMode}, #{snapshotType}, ",
			"#{goalNo}, #{dataSeq}, #{matchId}, #{dataCategory}, #{times}, #{homeRank}, ",
			"#{homeTeamName}, #{homeScore}, #{awayRank}, #{awayTeamName}, #{awayScore}, #{homeExp}, ",
			"#{awayExp}, #{homeInGoalExp}, #{awayInGoalExp}, #{homeDonation}, #{awayDonation}, #{homeShootAll}, ",
			"#{awayShootAll}, #{homeShootIn}, #{awayShootIn}, #{homeShootOut}, #{awayShootOut}, #{homeBlockShoot}, ",
			"#{awayBlockShoot}, #{homeBigChance}, #{awayBigChance}, #{homeCorner}, #{awayCorner}, #{homeBoxShootIn}, ",
			"#{awayBoxShootIn}, #{homeBoxShootOut}, #{awayBoxShootOut}, #{homeGoalPost}, #{awayGoalPost}, #{homeGoalHead}, ",
			"#{awayGoalHead}, #{homeKeeperSave}, #{awayKeeperSave}, #{homeFreeKick}, #{awayFreeKick}, #{homeOffside}, ",
			"#{awayOffside}, #{homeFoul}, #{awayFoul}, #{homeYellowCard}, #{awayYellowCard}, #{homeRedCard}, ",
			"#{awayRedCard}, #{homeSlowIn}, #{awaySlowIn}, #{homeBoxTouch}, #{awayBoxTouch}, #{homePassCount}, ",
			"#{awayPassCount}, #{homeLongPassCount}, #{awayLongPassCount}, #{homeFinalThirdPassCount}, #{awayFinalThirdPassCount}, #{homeCrossCount}, ",
			"#{awayCrossCount}, #{homeTackleCount}, #{awayTackleCount}, #{homeClearCount}, #{awayClearCount}, #{homeDuelCount}, ",
			"#{awayDuelCount}, #{homeInterceptCount}, #{awayInterceptCount}, #{recordTime}, #{weather}, #{temperature}, ",
			"#{humid}, #{judgeMember}, #{homeManager}, #{awayManager}, #{homeFormation}, #{awayFormation}, ",
			"#{studium}, #{capacity}, #{audience}, #{homeMaxGettingScorer}, #{awayMaxGettingScorer}, #{homeMaxGettingScorerGameSituation}, ",
			"#{awayMaxGettingScorerGameSituation}, #{homeTeamHomeScore}, #{homeTeamHomeLost}, #{awayTeamHomeScore}, #{awayTeamHomeLost}, #{homeTeamAwayScore}, ",
			"#{homeTeamAwayLost}, #{awayTeamAwayScore}, #{awayTeamAwayLost}, #{noticeFlg}, #{goalTime}, #{goalTeamMember}, ",
			"#{judge}, #{homeTeamStyle}, #{awayTeamStyle}, #{probablity}, #{predictionScoreTime}, ",
			"COALESCE(#{registerId}, 'SYSTEM'), COALESCE(CAST(#{registerTime} AS timestamptz), NOW()), ",
			"COALESCE(#{updateId}, 'SYSTEM'), COALESCE(CAST(#{updateTime} AS timestamptz), NOW())",
			") ON CONFLICT (season, country, league, home_team_name, away_team_name, snapshot_type, goal_no) DO UPDATE SET ",
			// seq・一意キー・register_* は変えない
			"classify_mode = EXCLUDED.classify_mode, ",
			"data_seq = EXCLUDED.data_seq, ",
			"match_id = EXCLUDED.match_id, ",
			"data_category = EXCLUDED.data_category, ",
			"times = EXCLUDED.times, ",
			"home_rank = EXCLUDED.home_rank, ",
			"home_score = EXCLUDED.home_score, ",
			"away_rank = EXCLUDED.away_rank, ",
			"away_score = EXCLUDED.away_score, ",
			"home_exp = EXCLUDED.home_exp, ",
			"away_exp = EXCLUDED.away_exp, ",
			"home_in_goal_exp = EXCLUDED.home_in_goal_exp, ",
			"away_in_goal_exp = EXCLUDED.away_in_goal_exp, ",
			"home_donation = EXCLUDED.home_donation, ",
			"away_donation = EXCLUDED.away_donation, ",
			"home_shoot_all = EXCLUDED.home_shoot_all, ",
			"away_shoot_all = EXCLUDED.away_shoot_all, ",
			"home_shoot_in = EXCLUDED.home_shoot_in, ",
			"away_shoot_in = EXCLUDED.away_shoot_in, ",
			"home_shoot_out = EXCLUDED.home_shoot_out, ",
			"away_shoot_out = EXCLUDED.away_shoot_out, ",
			"home_block_shoot = EXCLUDED.home_block_shoot, ",
			"away_block_shoot = EXCLUDED.away_block_shoot, ",
			"home_big_chance = EXCLUDED.home_big_chance, ",
			"away_big_chance = EXCLUDED.away_big_chance, ",
			"home_corner = EXCLUDED.home_corner, ",
			"away_corner = EXCLUDED.away_corner, ",
			"home_box_shoot_in = EXCLUDED.home_box_shoot_in, ",
			"away_box_shoot_in = EXCLUDED.away_box_shoot_in, ",
			"home_box_shoot_out = EXCLUDED.home_box_shoot_out, ",
			"away_box_shoot_out = EXCLUDED.away_box_shoot_out, ",
			"home_goal_post = EXCLUDED.home_goal_post, ",
			"away_goal_post = EXCLUDED.away_goal_post, ",
			"home_goal_head = EXCLUDED.home_goal_head, ",
			"away_goal_head = EXCLUDED.away_goal_head, ",
			"home_keeper_save = EXCLUDED.home_keeper_save, ",
			"away_keeper_save = EXCLUDED.away_keeper_save, ",
			"home_free_kick = EXCLUDED.home_free_kick, ",
			"away_free_kick = EXCLUDED.away_free_kick, ",
			"home_offside = EXCLUDED.home_offside, ",
			"away_offside = EXCLUDED.away_offside, ",
			"home_foul = EXCLUDED.home_foul, ",
			"away_foul = EXCLUDED.away_foul, ",
			"home_yellow_card = EXCLUDED.home_yellow_card, ",
			"away_yellow_card = EXCLUDED.away_yellow_card, ",
			"home_red_card = EXCLUDED.home_red_card, ",
			"away_red_card = EXCLUDED.away_red_card, ",
			"home_slow_in = EXCLUDED.home_slow_in, ",
			"away_slow_in = EXCLUDED.away_slow_in, ",
			"home_box_touch = EXCLUDED.home_box_touch, ",
			"away_box_touch = EXCLUDED.away_box_touch, ",
			"home_pass_count = EXCLUDED.home_pass_count, ",
			"away_pass_count = EXCLUDED.away_pass_count, ",
			"home_long_pass_count = EXCLUDED.home_long_pass_count, ",
			"away_long_pass_count = EXCLUDED.away_long_pass_count, ",
			"home_final_third_pass_count = EXCLUDED.home_final_third_pass_count, ",
			"away_final_third_pass_count = EXCLUDED.away_final_third_pass_count, ",
			"home_cross_count = EXCLUDED.home_cross_count, ",
			"away_cross_count = EXCLUDED.away_cross_count, ",
			"home_tackle_count = EXCLUDED.home_tackle_count, ",
			"away_tackle_count = EXCLUDED.away_tackle_count, ",
			"home_clear_count = EXCLUDED.home_clear_count, ",
			"away_clear_count = EXCLUDED.away_clear_count, ",
			"home_duel_count = EXCLUDED.home_duel_count, ",
			"away_duel_count = EXCLUDED.away_duel_count, ",
			"home_intercept_count = EXCLUDED.home_intercept_count, ",
			"away_intercept_count = EXCLUDED.away_intercept_count, ",
			"record_time = EXCLUDED.record_time, ",
			"weather = EXCLUDED.weather, ",
			"temperature = EXCLUDED.temperature, ",
			"humid = EXCLUDED.humid, ",
			"judge_member = EXCLUDED.judge_member, ",
			"home_manager = EXCLUDED.home_manager, ",
			"away_manager = EXCLUDED.away_manager, ",
			"home_formation = EXCLUDED.home_formation, ",
			"away_formation = EXCLUDED.away_formation, ",
			"studium = EXCLUDED.studium, ",
			"capacity = EXCLUDED.capacity, ",
			"audience = EXCLUDED.audience, ",
			"home_max_getting_scorer = EXCLUDED.home_max_getting_scorer, ",
			"away_max_getting_scorer = EXCLUDED.away_max_getting_scorer, ",
			"home_max_getting_scorer_game_situation = EXCLUDED.home_max_getting_scorer_game_situation, ",
			"away_max_getting_scorer_game_situation = EXCLUDED.away_max_getting_scorer_game_situation, ",
			"home_team_home_score = EXCLUDED.home_team_home_score, ",
			"home_team_home_lost = EXCLUDED.home_team_home_lost, ",
			"away_team_home_score = EXCLUDED.away_team_home_score, ",
			"away_team_home_lost = EXCLUDED.away_team_home_lost, ",
			"home_team_away_score = EXCLUDED.home_team_away_score, ",
			"home_team_away_lost = EXCLUDED.home_team_away_lost, ",
			"away_team_away_score = EXCLUDED.away_team_away_score, ",
			"away_team_away_lost = EXCLUDED.away_team_away_lost, ",
			"notice_flg = EXCLUDED.notice_flg, ",
			"goal_time = EXCLUDED.goal_time, ",
			"goal_team_member = EXCLUDED.goal_team_member, ",
			"judge = EXCLUDED.judge, ",
			"home_team_style = EXCLUDED.home_team_style, ",
			"away_team_style = EXCLUDED.away_team_style, ",
			"probablity = EXCLUDED.probablity, ",
			"prediction_score_time = EXCLUDED.prediction_score_time, ",
			"update_id = EXCLUDED.update_id, update_time = EXCLUDED.update_time"
	})
	int upsert(MatchClassificationResultEntity entity);
}
