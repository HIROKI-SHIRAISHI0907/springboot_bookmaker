package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m021.TeamMatchFinalStatsEntity;

/**
 * team_match_final_stats Mapper（BM_M021 チーム視点の試合最終成績）。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>{@link #upsert}: 1行（1試合 × 1チーム視点）を UPSERT する。一意キー
 *       (season, country, league, team_name, versus_team_name, ha) が既にあれば上書き。</li>
 *   <li>{@link #findSeq}: 既存行の seq を取得する（再処理で番号を消費しないため）。</li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT）。</li>
 *   <li><b>メタデータは NOT NULL</b>。Entity に値がなければ ID='SYSTEM'、日時=NOW()。更新時は register_* を変えない。</li>
 *   <li><b>同時実行で同じ試合を2つの処理が新規として採番した場合</b>: 後の INSERT は更新になり、後の番号は欠番になる。</li>
 * </ul>
 */
@Mapper
public interface TeamMatchFinalStatsRepository {

	/**
	 * 既存行の seq（なければ null）。
	 */
	@Select({
			"SELECT seq FROM team_match_final_stats ",
			"WHERE season = #{season} AND country = #{country} AND league = #{league} ",
			"AND team_name = #{teamName} AND versus_team_name = #{versusTeamName} AND ha = #{ha}"
	})
	String findSeq(
			@Param("season") String season,
			@Param("country") String country,
			@Param("league") String league,
			@Param("teamName") String teamName,
			@Param("versusTeamName") String versusTeamName,
			@Param("ha") String ha);

	/**
	 * 1行を UPSERT する。
	 *
	 * @param entity 登録対象
	 * @return 処理件数（INSERT・UPDATE とも 1）
	 */
	@Insert({
			"INSERT INTO team_match_final_stats (",
			"seq, season, country, league, team_name, ",
			"versus_team_name, ha, match_id, goals_for, goals_against, ",
			"score, result, game_fin_rank, opposite_game_fin_rank, exp, ",
			"opposite_exp, in_goal_exp, opposite_in_goal_exp, donation, opposite_donation, ",
			"shoot_all, opposite_shoot_all, shoot_in, opposite_shoot_in, shoot_out, ",
			"opposite_shoot_out, block_shoot, opposite_block_shoot, big_chance, opposite_big_chance, ",
			"corner, opposite_corner, box_shoot_in, opposite_box_shoot_in, box_shoot_out, ",
			"opposite_box_shoot_out, goal_post, opposite_goal_post, goal_head, opposite_goal_head, ",
			"keeper_save, opposite_keeper_save, free_kick, opposite_free_kick, offside, ",
			"opposite_offside, foul, opposite_foul, yellow_card, opposite_yellow_card, ",
			"red_card, opposite_red_card, slow_in, opposite_slow_in, box_touch, ",
			"opposite_box_touch, pass_count_success_ratio, pass_count_success_count, pass_count_try_count, opposite_pass_count_success_ratio, ",
			"opposite_pass_count_success_count, opposite_pass_count_try_count, long_pass_count_success_ratio, long_pass_count_success_count, long_pass_count_try_count, ",
			"opposite_long_pass_count_success_ratio, opposite_long_pass_count_success_count, opposite_long_pass_count_try_count, final_third_pass_count_success_ratio, final_third_pass_count_success_count, ",
			"final_third_pass_count_try_count, opposite_final_third_pass_count_success_ratio, opposite_final_third_pass_count_success_count, opposite_final_third_pass_count_try_count, cross_count_success_ratio, ",
			"cross_count_success_count, cross_count_try_count, opposite_cross_count_success_ratio, opposite_cross_count_success_count, opposite_cross_count_try_count, ",
			"tackle_count_success_ratio, tackle_count_success_count, tackle_count_try_count, opposite_tackle_count_success_ratio, opposite_tackle_count_success_count, ",
			"opposite_tackle_count_try_count, clear_count, opposite_clear_count, duel_count, opposite_duel_count, ",
			"intercept_count, opposite_intercept_count, weather, temperature, humid, ",
			"register_id, register_time, update_id, update_time",
			") VALUES (",
			"#{seq}, #{season}, #{country}, #{league}, #{teamName}, ",
			"#{versusTeamName}, #{ha}, #{matchId}, #{goalsFor}, #{goalsAgainst}, ",
			"#{score}, #{result}, #{gameFinRank}, #{oppositeGameFinRank}, #{exp}, ",
			"#{oppositeExp}, #{inGoalExp}, #{oppositeInGoalExp}, #{donation}, #{oppositeDonation}, ",
			"#{shootAll}, #{oppositeShootAll}, #{shootIn}, #{oppositeShootIn}, #{shootOut}, ",
			"#{oppositeShootOut}, #{blockShoot}, #{oppositeBlockShoot}, #{bigChance}, #{oppositeBigChance}, ",
			"#{corner}, #{oppositeCorner}, #{boxShootIn}, #{oppositeBoxShootIn}, #{boxShootOut}, ",
			"#{oppositeBoxShootOut}, #{goalPost}, #{oppositeGoalPost}, #{goalHead}, #{oppositeGoalHead}, ",
			"#{keeperSave}, #{oppositeKeeperSave}, #{freeKick}, #{oppositeFreeKick}, #{offside}, ",
			"#{oppositeOffside}, #{foul}, #{oppositeFoul}, #{yellowCard}, #{oppositeYellowCard}, ",
			"#{redCard}, #{oppositeRedCard}, #{slowIn}, #{oppositeSlowIn}, #{boxTouch}, ",
			"#{oppositeBoxTouch}, #{passCountSuccessRatio}, #{passCountSuccessCount}, #{passCountTryCount}, #{oppositePassCountSuccessRatio}, ",
			"#{oppositePassCountSuccessCount}, #{oppositePassCountTryCount}, #{longPassCountSuccessRatio}, #{longPassCountSuccessCount}, #{longPassCountTryCount}, ",
			"#{oppositeLongPassCountSuccessRatio}, #{oppositeLongPassCountSuccessCount}, #{oppositeLongPassCountTryCount}, #{finalThirdPassCountSuccessRatio}, #{finalThirdPassCountSuccessCount}, ",
			"#{finalThirdPassCountTryCount}, #{oppositeFinalThirdPassCountSuccessRatio}, #{oppositeFinalThirdPassCountSuccessCount}, #{oppositeFinalThirdPassCountTryCount}, #{crossCountSuccessRatio}, ",
			"#{crossCountSuccessCount}, #{crossCountTryCount}, #{oppositeCrossCountSuccessRatio}, #{oppositeCrossCountSuccessCount}, #{oppositeCrossCountTryCount}, ",
			"#{tackleCountSuccessRatio}, #{tackleCountSuccessCount}, #{tackleCountTryCount}, #{oppositeTackleCountSuccessRatio}, #{oppositeTackleCountSuccessCount}, ",
			"#{oppositeTackleCountTryCount}, #{clearCount}, #{oppositeClearCount}, #{duelCount}, #{oppositeDuelCount}, ",
			"#{interceptCount}, #{oppositeInterceptCount}, #{weather}, #{temperature}, #{humid}, ",
			"COALESCE(#{registerId}, 'SYSTEM'), COALESCE(CAST(#{registerTime} AS timestamptz), NOW()), ",
			"COALESCE(#{updateId}, 'SYSTEM'), COALESCE(CAST(#{updateTime} AS timestamptz), NOW())",
			") ON CONFLICT (season, country, league, team_name, versus_team_name, ha) DO UPDATE SET ",
			// seq・一意キー・register_* は変えない
			"match_id = EXCLUDED.match_id, ",
			"goals_for = EXCLUDED.goals_for, ",
			"goals_against = EXCLUDED.goals_against, ",
			"score = EXCLUDED.score, ",
			"result = EXCLUDED.result, ",
			"game_fin_rank = EXCLUDED.game_fin_rank, ",
			"opposite_game_fin_rank = EXCLUDED.opposite_game_fin_rank, ",
			"exp = EXCLUDED.exp, ",
			"opposite_exp = EXCLUDED.opposite_exp, ",
			"in_goal_exp = EXCLUDED.in_goal_exp, ",
			"opposite_in_goal_exp = EXCLUDED.opposite_in_goal_exp, ",
			"donation = EXCLUDED.donation, ",
			"opposite_donation = EXCLUDED.opposite_donation, ",
			"shoot_all = EXCLUDED.shoot_all, ",
			"opposite_shoot_all = EXCLUDED.opposite_shoot_all, ",
			"shoot_in = EXCLUDED.shoot_in, ",
			"opposite_shoot_in = EXCLUDED.opposite_shoot_in, ",
			"shoot_out = EXCLUDED.shoot_out, ",
			"opposite_shoot_out = EXCLUDED.opposite_shoot_out, ",
			"block_shoot = EXCLUDED.block_shoot, ",
			"opposite_block_shoot = EXCLUDED.opposite_block_shoot, ",
			"big_chance = EXCLUDED.big_chance, ",
			"opposite_big_chance = EXCLUDED.opposite_big_chance, ",
			"corner = EXCLUDED.corner, ",
			"opposite_corner = EXCLUDED.opposite_corner, ",
			"box_shoot_in = EXCLUDED.box_shoot_in, ",
			"opposite_box_shoot_in = EXCLUDED.opposite_box_shoot_in, ",
			"box_shoot_out = EXCLUDED.box_shoot_out, ",
			"opposite_box_shoot_out = EXCLUDED.opposite_box_shoot_out, ",
			"goal_post = EXCLUDED.goal_post, ",
			"opposite_goal_post = EXCLUDED.opposite_goal_post, ",
			"goal_head = EXCLUDED.goal_head, ",
			"opposite_goal_head = EXCLUDED.opposite_goal_head, ",
			"keeper_save = EXCLUDED.keeper_save, ",
			"opposite_keeper_save = EXCLUDED.opposite_keeper_save, ",
			"free_kick = EXCLUDED.free_kick, ",
			"opposite_free_kick = EXCLUDED.opposite_free_kick, ",
			"offside = EXCLUDED.offside, ",
			"opposite_offside = EXCLUDED.opposite_offside, ",
			"foul = EXCLUDED.foul, ",
			"opposite_foul = EXCLUDED.opposite_foul, ",
			"yellow_card = EXCLUDED.yellow_card, ",
			"opposite_yellow_card = EXCLUDED.opposite_yellow_card, ",
			"red_card = EXCLUDED.red_card, ",
			"opposite_red_card = EXCLUDED.opposite_red_card, ",
			"slow_in = EXCLUDED.slow_in, ",
			"opposite_slow_in = EXCLUDED.opposite_slow_in, ",
			"box_touch = EXCLUDED.box_touch, ",
			"opposite_box_touch = EXCLUDED.opposite_box_touch, ",
			"pass_count_success_ratio = EXCLUDED.pass_count_success_ratio, ",
			"pass_count_success_count = EXCLUDED.pass_count_success_count, ",
			"pass_count_try_count = EXCLUDED.pass_count_try_count, ",
			"opposite_pass_count_success_ratio = EXCLUDED.opposite_pass_count_success_ratio, ",
			"opposite_pass_count_success_count = EXCLUDED.opposite_pass_count_success_count, ",
			"opposite_pass_count_try_count = EXCLUDED.opposite_pass_count_try_count, ",
			"long_pass_count_success_ratio = EXCLUDED.long_pass_count_success_ratio, ",
			"long_pass_count_success_count = EXCLUDED.long_pass_count_success_count, ",
			"long_pass_count_try_count = EXCLUDED.long_pass_count_try_count, ",
			"opposite_long_pass_count_success_ratio = EXCLUDED.opposite_long_pass_count_success_ratio, ",
			"opposite_long_pass_count_success_count = EXCLUDED.opposite_long_pass_count_success_count, ",
			"opposite_long_pass_count_try_count = EXCLUDED.opposite_long_pass_count_try_count, ",
			"final_third_pass_count_success_ratio = EXCLUDED.final_third_pass_count_success_ratio, ",
			"final_third_pass_count_success_count = EXCLUDED.final_third_pass_count_success_count, ",
			"final_third_pass_count_try_count = EXCLUDED.final_third_pass_count_try_count, ",
			"opposite_final_third_pass_count_success_ratio = EXCLUDED.opposite_final_third_pass_count_success_ratio, ",
			"opposite_final_third_pass_count_success_count = EXCLUDED.opposite_final_third_pass_count_success_count, ",
			"opposite_final_third_pass_count_try_count = EXCLUDED.opposite_final_third_pass_count_try_count, ",
			"cross_count_success_ratio = EXCLUDED.cross_count_success_ratio, ",
			"cross_count_success_count = EXCLUDED.cross_count_success_count, ",
			"cross_count_try_count = EXCLUDED.cross_count_try_count, ",
			"opposite_cross_count_success_ratio = EXCLUDED.opposite_cross_count_success_ratio, ",
			"opposite_cross_count_success_count = EXCLUDED.opposite_cross_count_success_count, ",
			"opposite_cross_count_try_count = EXCLUDED.opposite_cross_count_try_count, ",
			"tackle_count_success_ratio = EXCLUDED.tackle_count_success_ratio, ",
			"tackle_count_success_count = EXCLUDED.tackle_count_success_count, ",
			"tackle_count_try_count = EXCLUDED.tackle_count_try_count, ",
			"opposite_tackle_count_success_ratio = EXCLUDED.opposite_tackle_count_success_ratio, ",
			"opposite_tackle_count_success_count = EXCLUDED.opposite_tackle_count_success_count, ",
			"opposite_tackle_count_try_count = EXCLUDED.opposite_tackle_count_try_count, ",
			"clear_count = EXCLUDED.clear_count, ",
			"opposite_clear_count = EXCLUDED.opposite_clear_count, ",
			"duel_count = EXCLUDED.duel_count, ",
			"opposite_duel_count = EXCLUDED.opposite_duel_count, ",
			"intercept_count = EXCLUDED.intercept_count, ",
			"opposite_intercept_count = EXCLUDED.opposite_intercept_count, ",
			"weather = EXCLUDED.weather, ",
			"temperature = EXCLUDED.temperature, ",
			"humid = EXCLUDED.humid, ",
			"update_id = EXCLUDED.update_id, update_time = EXCLUDED.update_time"
	})
	int upsert(TeamMatchFinalStatsEntity entity);
}
