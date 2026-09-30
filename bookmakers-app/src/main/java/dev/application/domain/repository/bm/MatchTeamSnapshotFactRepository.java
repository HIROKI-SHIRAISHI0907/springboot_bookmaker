package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m034.MatchTeamSnapshotFactEntity;

/**
 * match_team_snapshot_fact Mapper（BM_M034 試合中スナップショット）。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>{@link #findSeqByDataSeqs}: 既存行の（data_seq, ha, seq）を取得する（再送で番号を消費しないため）。</li>
 *   <li>{@link #upsertBatch}: 複数行をまとめて UPSERT する（一意キー (data_seq, ha)）。</li>
 * </ul>
 * <p>差分の読み取りはビュー match_team_snapshot_diff / match_team_snapshot_latest から行うこと。</p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT）。</li>
 *   <li><b>1回の upsertBatch の行数</b>: 1行あたり約70パラメータ。PostgreSQL の上限（65535）を超えないよう、
 *       呼び出し側で 900 行以下に分けること（Writer は 200 行ずつ）。</li>
 *   <li><b>同一文内で同じキーが2回あるとエラー</b>。Writer で重複を除いている。</li>
 *   <li><b>メタデータは NOT NULL</b>。Entity に値がなければ ID='SYSTEM'、日時=NOW()。更新時は seq・register_* を変えない。</li>
 * </ul>
 */
@Mapper
public interface MatchTeamSnapshotFactRepository {

	/**
	 * 既存行の（data_seq, ha, seq）。
	 */
	@Select({
			"<script>",
			"SELECT data_seq, ha, seq FROM match_team_snapshot_fact WHERE data_seq IN ",
			"<foreach collection='dataSeqs' item='d' open='(' separator=',' close=')'>#{d}</foreach>",
			"</script>"
	})
	@Results(id = "mtsfSeq", value = {
			@Result(column = "data_seq", property = "dataSeq"),
			@Result(column = "ha", property = "ha"),
			@Result(column = "seq", property = "seq")
	})
	List<MatchTeamSnapshotFactEntity> findSeqByDataSeqs(@Param("dataSeqs") List<Long> dataSeqs);

	/**
	 * 複数行をまとめて UPSERT する。
	 *
	 * @param rows 登録対象（seq・season 設定済み。同じ (dataSeq, ha) を含めないこと）
	 * @return 処理件数（INSERT・UPDATE とも1行1件）
	 */
	@Insert({
			"<script>",
			"INSERT INTO match_team_snapshot_fact (",
			"seq, season, country, league, match_id, team, ",
			"opponent, ha, data_seq, round_no, half, match_time_label, ",
			"match_minute, fin_flg, record_time, team_score, opponent_score, score_diff, ",
			"possession, exp, in_goal_exp, shoot_all, shoot_in, shoot_out, ",
			"block_shoot, big_chance, corner, box_shoot_in, box_shoot_out, goal_post, ",
			"goal_head, keeper_save, free_kick, offside, foul, yellow_card, ",
			"red_card, slow_in, box_touch, clear_count, duel_count, intercept_count, ",
			"pass_count_success, pass_count_try, pass_count_rate, long_pass_count_success, long_pass_count_try, long_pass_count_rate, ",
			"final_third_pass_count_success, final_third_pass_count_try, final_third_pass_count_rate, cross_count_success, cross_count_try, cross_count_rate, ",
			"tackle_count_success, tackle_count_try, tackle_count_rate, ",
			"register_id, register_time, update_id, update_time",
			") VALUES ",
			"<foreach collection='rows' item='r' separator=','>",
			"(",
			"#{r.seq}, #{r.season}, #{r.country}, #{r.league}, #{r.matchId}, #{r.team}, ",
			"#{r.opponent}, #{r.ha}, #{r.dataSeq}, #{r.roundNo}, #{r.half}, #{r.matchTimeLabel}, ",
			"#{r.matchMinute}, #{r.finFlg}, #{r.recordTime}, #{r.teamScore}, #{r.opponentScore}, #{r.scoreDiff}, ",
			"#{r.possession}, #{r.exp}, #{r.inGoalExp}, #{r.shootAll}, #{r.shootIn}, #{r.shootOut}, ",
			"#{r.blockShoot}, #{r.bigChance}, #{r.corner}, #{r.boxShootIn}, #{r.boxShootOut}, #{r.goalPost}, ",
			"#{r.goalHead}, #{r.keeperSave}, #{r.freeKick}, #{r.offside}, #{r.foul}, #{r.yellowCard}, ",
			"#{r.redCard}, #{r.slowIn}, #{r.boxTouch}, #{r.clearCount}, #{r.duelCount}, #{r.interceptCount}, ",
			"#{r.passCountSuccess}, #{r.passCountTry}, #{r.passCountRate}, #{r.longPassCountSuccess}, #{r.longPassCountTry}, #{r.longPassCountRate}, ",
			"#{r.finalThirdPassCountSuccess}, #{r.finalThirdPassCountTry}, #{r.finalThirdPassCountRate}, #{r.crossCountSuccess}, #{r.crossCountTry}, #{r.crossCountRate}, ",
			"#{r.tackleCountSuccess}, #{r.tackleCountTry}, #{r.tackleCountRate}, ",
			"COALESCE(#{r.registerId}, 'SYSTEM'), COALESCE(CAST(#{r.registerTime} AS timestamptz), NOW()), ",
			"COALESCE(#{r.updateId}, 'SYSTEM'), COALESCE(CAST(#{r.updateTime} AS timestamptz), NOW())",
			")",
			"</foreach>",
			" ON CONFLICT (data_seq, ha) DO UPDATE SET ",
			// seq・一意キー・register_* は変えない
			"season = EXCLUDED.season, country = EXCLUDED.country, league = EXCLUDED.league, ",
			"match_id = EXCLUDED.match_id, team = EXCLUDED.team, opponent = EXCLUDED.opponent, ",
			"round_no = EXCLUDED.round_no, half = EXCLUDED.half, match_time_label = EXCLUDED.match_time_label, ",
			"match_minute = EXCLUDED.match_minute, fin_flg = EXCLUDED.fin_flg, record_time = EXCLUDED.record_time, ",
			"team_score = EXCLUDED.team_score, opponent_score = EXCLUDED.opponent_score, score_diff = EXCLUDED.score_diff, ",
			"possession = EXCLUDED.possession, exp = EXCLUDED.exp, in_goal_exp = EXCLUDED.in_goal_exp, ",
			"shoot_all = EXCLUDED.shoot_all, shoot_in = EXCLUDED.shoot_in, shoot_out = EXCLUDED.shoot_out, ",
			"block_shoot = EXCLUDED.block_shoot, big_chance = EXCLUDED.big_chance, corner = EXCLUDED.corner, ",
			"box_shoot_in = EXCLUDED.box_shoot_in, box_shoot_out = EXCLUDED.box_shoot_out, goal_post = EXCLUDED.goal_post, ",
			"goal_head = EXCLUDED.goal_head, keeper_save = EXCLUDED.keeper_save, free_kick = EXCLUDED.free_kick, ",
			"offside = EXCLUDED.offside, foul = EXCLUDED.foul, yellow_card = EXCLUDED.yellow_card, ",
			"red_card = EXCLUDED.red_card, slow_in = EXCLUDED.slow_in, box_touch = EXCLUDED.box_touch, ",
			"clear_count = EXCLUDED.clear_count, duel_count = EXCLUDED.duel_count, intercept_count = EXCLUDED.intercept_count, ",
			"pass_count_success = EXCLUDED.pass_count_success, pass_count_try = EXCLUDED.pass_count_try, pass_count_rate = EXCLUDED.pass_count_rate, ",
			"long_pass_count_success = EXCLUDED.long_pass_count_success, long_pass_count_try = EXCLUDED.long_pass_count_try, long_pass_count_rate = EXCLUDED.long_pass_count_rate, ",
			"final_third_pass_count_success = EXCLUDED.final_third_pass_count_success, final_third_pass_count_try = EXCLUDED.final_third_pass_count_try, final_third_pass_count_rate = EXCLUDED.final_third_pass_count_rate, ",
			"cross_count_success = EXCLUDED.cross_count_success, cross_count_try = EXCLUDED.cross_count_try, cross_count_rate = EXCLUDED.cross_count_rate, ",
			"tackle_count_success = EXCLUDED.tackle_count_success, tackle_count_try = EXCLUDED.tackle_count_try, tackle_count_rate = EXCLUDED.tackle_count_rate, ",
			"update_id = EXCLUDED.update_id, update_time = EXCLUDED.update_time",
			"</script>"
	})
	int upsertBatch(@Param("rows") List<MatchTeamSnapshotFactEntity> rows);
}