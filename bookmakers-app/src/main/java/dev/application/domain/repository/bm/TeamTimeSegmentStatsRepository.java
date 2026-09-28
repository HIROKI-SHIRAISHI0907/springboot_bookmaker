package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m004.TeamTimeSegmentStatsEntity;

/**
 * team_time_segment_stats Mapper（BM_M004 時間帯別 対戦成績・縦持ち）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * 1行を UPSERT する。一意キー (season, data_category, team_name, opponent_team_name, ha, time_segment) が
 * 既にあれば値を上書きする。同じ試合を再処理しても行は増えず、最新の計算結果で上書きされる（冪等）。
 * </p>
 * <p>
 * 主キーは seq（「&lt;シーズン&gt;-&lt;6桁枝番&gt;」）。seq は Writer が seq_counter で採番して渡す。
 * 既存行の seq は {@link #findSeqByMatchKey} で取得して使い回し、再処理で番号を消費しないようにする。
 * ON CONFLICT で更新になった場合も seq は変えない（既存行の番号のまま）。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT）。</li>
 *   <li><b>メタデータ（register_id / register_time / update_id / update_time）は NOT NULL</b>。
 *       Entity（MetaEntity）に値があればそれを使い、null の場合は ID='SYSTEM'、日時=NOW() を入れる。
 *       UPDATE（ON CONFLICT）時は register_* を変えず、update_* だけ更新する。</li>
 *   <li><b>同時実行で同じ試合の新規行を2つの処理が採番した場合</b>: 後の INSERT は ON CONFLICT で更新になり、
 *       後の処理が採番した番号は使われず欠番になる（行の重複・seq の変更は起きない）。</li>
 *   <li><b>同じ組み合わせの試合がシーズン内に2試合ある場合</b>（カップ戦の混入・プレーオフなど）、後の試合で上書きされる。</li>
 * </ul>
 */
@Mapper
public interface TeamTimeSegmentStatsRepository {

	/**
	 * 1チーム分（対象・相手・H/A）の既存行の時間帯と seq を取得する。
	 * 一意キーの先頭5列で引くため、一意制約のインデックスが使われる（最大11行）。
	 *
	 * @return 既存行（timeSegment と seq のみ設定。なければ空）
	 */
	@Select({
			"SELECT time_segment, seq FROM team_time_segment_stats ",
			"WHERE season = #{season} AND data_category = #{dataCategory} ",
			"AND team_name = #{teamName} AND opponent_team_name = #{opponentTeamName} AND ha = #{ha}"
	})
	@Results(id = "timeSegmentSeq", value = {
			@Result(column = "time_segment", property = "timeSegment"),
			@Result(column = "seq", property = "seq")
	})
	List<TeamTimeSegmentStatsEntity> findSeqByMatchKey(
			@Param("season") String season,
			@Param("dataCategory") String dataCategory,
			@Param("teamName") String teamName,
			@Param("opponentTeamName") String opponentTeamName,
			@Param("ha") String ha);

	/**
	 * 1行を UPSERT する。
	 * @param entity 登録対象
	 * @return 処理件数（INSERT・UPDATE とも 1）
	 */
	@Insert({
			"INSERT INTO team_time_segment_stats (",
			"seq, season, data_category, team_name, opponent_team_name, ha, match_id, ",
			"time_segment, segment_order, snapshot_count, goal_for, goal_against, exp, ",
			"in_goal_exp, possession, shoot_all, shoot_in, shoot_out, shoot_blocked, ",
			"big_chance, corner_kick, box_shoot_in, box_shoot_out, goal_post, goal_head, ",
			"keeper_save, free_kick, offside, foul, yellow_card, red_card, ",
			"slow_in, box_touch, clear_count, duel_count, intercept_count, pass_success, ",
			"pass_try, pass_rate, long_pass_success, long_pass_try, long_pass_rate, final_third_pass_success, ",
			"final_third_pass_try, final_third_pass_rate, cross_success, cross_try, cross_rate, tackle_success, ",
			"tackle_try, tackle_rate, ",
			"register_id, register_time, update_id, update_time",
			") VALUES (",
			"#{seq}, #{season}, #{dataCategory}, #{teamName}, #{opponentTeamName}, #{ha}, #{matchId}, ",
			"#{timeSegment}, #{segmentOrder}, #{snapshotCount}, #{goalFor}, #{goalAgainst}, #{exp}, ",
			"#{inGoalExp}, #{possession}, #{shootAll}, #{shootIn}, #{shootOut}, #{shootBlocked}, ",
			"#{bigChance}, #{cornerKick}, #{boxShootIn}, #{boxShootOut}, #{goalPost}, #{goalHead}, ",
			"#{keeperSave}, #{freeKick}, #{offside}, #{foul}, #{yellowCard}, #{redCard}, ",
			"#{slowIn}, #{boxTouch}, #{clearCount}, #{duelCount}, #{interceptCount}, #{passSuccess}, ",
			"#{passTry}, #{passRate}, #{longPassSuccess}, #{longPassTry}, #{longPassRate}, #{finalThirdPassSuccess}, ",
			"#{finalThirdPassTry}, #{finalThirdPassRate}, #{crossSuccess}, #{crossTry}, #{crossRate}, #{tackleSuccess}, ",
			"#{tackleTry}, #{tackleRate}, ",
			"COALESCE(#{registerId}, 'SYSTEM'), COALESCE(CAST(#{registerTime} AS timestamptz), NOW()), ",
			"COALESCE(#{updateId}, 'SYSTEM'), COALESCE(CAST(#{updateTime} AS timestamptz), NOW())",
			") ON CONFLICT (season, data_category, team_name, opponent_team_name, ha, time_segment) DO UPDATE SET ",
			// seq・一意キー・register_* は変えない
			"match_id = EXCLUDED.match_id, ",
			"segment_order = EXCLUDED.segment_order, ",
			"snapshot_count = EXCLUDED.snapshot_count, ",
			"goal_for = EXCLUDED.goal_for, ",
			"goal_against = EXCLUDED.goal_against, ",
			"exp = EXCLUDED.exp, ",
			"in_goal_exp = EXCLUDED.in_goal_exp, ",
			"possession = EXCLUDED.possession, ",
			"shoot_all = EXCLUDED.shoot_all, ",
			"shoot_in = EXCLUDED.shoot_in, ",
			"shoot_out = EXCLUDED.shoot_out, ",
			"shoot_blocked = EXCLUDED.shoot_blocked, ",
			"big_chance = EXCLUDED.big_chance, ",
			"corner_kick = EXCLUDED.corner_kick, ",
			"box_shoot_in = EXCLUDED.box_shoot_in, ",
			"box_shoot_out = EXCLUDED.box_shoot_out, ",
			"goal_post = EXCLUDED.goal_post, ",
			"goal_head = EXCLUDED.goal_head, ",
			"keeper_save = EXCLUDED.keeper_save, ",
			"free_kick = EXCLUDED.free_kick, ",
			"offside = EXCLUDED.offside, ",
			"foul = EXCLUDED.foul, ",
			"yellow_card = EXCLUDED.yellow_card, ",
			"red_card = EXCLUDED.red_card, ",
			"slow_in = EXCLUDED.slow_in, ",
			"box_touch = EXCLUDED.box_touch, ",
			"clear_count = EXCLUDED.clear_count, ",
			"duel_count = EXCLUDED.duel_count, ",
			"intercept_count = EXCLUDED.intercept_count, ",
			"pass_success = EXCLUDED.pass_success, ",
			"pass_try = EXCLUDED.pass_try, ",
			"pass_rate = EXCLUDED.pass_rate, ",
			"long_pass_success = EXCLUDED.long_pass_success, ",
			"long_pass_try = EXCLUDED.long_pass_try, ",
			"long_pass_rate = EXCLUDED.long_pass_rate, ",
			"final_third_pass_success = EXCLUDED.final_third_pass_success, ",
			"final_third_pass_try = EXCLUDED.final_third_pass_try, ",
			"final_third_pass_rate = EXCLUDED.final_third_pass_rate, ",
			"cross_success = EXCLUDED.cross_success, ",
			"cross_try = EXCLUDED.cross_try, ",
			"cross_rate = EXCLUDED.cross_rate, ",
			"tackle_success = EXCLUDED.tackle_success, ",
			"tackle_try = EXCLUDED.tackle_try, ",
			"tackle_rate = EXCLUDED.tackle_rate, ",
			"update_id = EXCLUDED.update_id, update_time = EXCLUDED.update_time"
	})
	int upsert(TeamTimeSegmentStatsEntity entity);
}