package dev.batch.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.common.entity.MatchDetailMatchEntity;
import dev.common.entity.MatchDetailMomentumEntity;
import dev.common.entity.MatchDetailMomentumIncidentEntity;
import dev.common.entity.MatchDetailSummaryEntity;
import dev.common.entity.MatchDetailGoalEntity;
import dev.common.entity.MatchDetailFormationEntity;
import dev.common.entity.MatchDetailLineupEntity;
import dev.common.entity.MatchDetailMissingEntity;
import dev.common.entity.MatchDetailMemberStatEntity;

/**
 * BM_B016 試合詳細データ（match_detail_*）の Repository。
 *
 * <p>1 試合の登録は「その試合の行を消して入れ直す」（{@code deleteXxx} → {@code insertXxx}）。
 * 一覧系は foreach でまとめて INSERT し、row_no は CSV の行順（1 始まり）を入れる。</p>
 * <p>match_date は CSV の文字列（yyyy-MM-dd）を DATE に変換して入れる（空なら NULL）。</p>
 * @author shiraishitoshio
 */
@Mapper
public interface MatchDetailBatchRepository {

	/**
	 * 試合が登録済みか（match_detail_match にあるか）
	 * @param matchId 試合ID
	 * @return 件数
	 */
	@Select("SELECT COUNT(*) FROM match_detail_match WHERE match_id = #{matchId}")
	int countMatch(@Param("matchId") String matchId);

	/**
	 * 試合詳細: 試合の基本情報: 試合の行を削除
	 * @param matchId 試合ID
	 * @return 削除件数
	 */
	@Delete("DELETE FROM match_detail_match WHERE match_id = #{matchId}")
	int deleteMatch(@Param("matchId") String matchId);

	/**
	 * 試合詳細: マッチモメンタム（1分1行）: 試合の行を削除
	 * @param matchId 試合ID
	 * @return 削除件数
	 */
	@Delete("DELETE FROM match_detail_momentum WHERE match_id = #{matchId}")
	int deleteMomentum(@Param("matchId") String matchId);

	/**
	 * 試合詳細: モメンタム上のアイコン: 試合の行を削除
	 * @param matchId 試合ID
	 * @return 削除件数
	 */
	@Delete("DELETE FROM match_detail_momentum_incident WHERE match_id = #{matchId}")
	int deleteMomentumIncident(@Param("matchId") String matchId);

	/**
	 * 試合詳細: サマリーの出来事: 試合の行を削除
	 * @param matchId 試合ID
	 * @return 削除件数
	 */
	@Delete("DELETE FROM match_detail_summary WHERE match_id = #{matchId}")
	int deleteSummary(@Param("matchId") String matchId);

	/**
	 * 試合詳細: ゴール: 試合の行を削除
	 * @param matchId 試合ID
	 * @return 削除件数
	 */
	@Delete("DELETE FROM match_detail_goal WHERE match_id = #{matchId}")
	int deleteGoal(@Param("matchId") String matchId);

	/**
	 * 試合詳細: フォーメーション: 試合の行を削除
	 * @param matchId 試合ID
	 * @return 削除件数
	 */
	@Delete("DELETE FROM match_detail_formation WHERE match_id = #{matchId}")
	int deleteFormation(@Param("matchId") String matchId);

	/**
	 * 試合詳細: ラインアップ: 試合の行を削除
	 * @param matchId 試合ID
	 * @return 削除件数
	 */
	@Delete("DELETE FROM match_detail_lineup WHERE match_id = #{matchId}")
	int deleteLineup(@Param("matchId") String matchId);

	/**
	 * 試合詳細: 欠場選手: 試合の行を削除
	 * @param matchId 試合ID
	 * @return 削除件数
	 */
	@Delete("DELETE FROM match_detail_missing WHERE match_id = #{matchId}")
	int deleteMissing(@Param("matchId") String matchId);

	/**
	 * 試合詳細: 選手スタッツ（全タブまとめ）: 試合の行を削除
	 * @param matchId 試合ID
	 * @return 削除件数
	 */
	@Delete("DELETE FROM match_detail_member_stat WHERE match_id = #{matchId}")
	int deleteMemberStat(@Param("matchId") String matchId);

	/**
	 * 試合詳細: 試合の基本情報: 登録
	 * @param e 試合の基本情報
	 * @param scrapeErrors manifest の errors（カンマ区切り）
	 * @param folderKey S3 のフォルダ
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO match_detail_match (
			    match_id,
			    match_date,
			    home_team_name,
			    away_team_name,
			    category,
			    home_score,
			    away_score,
			    status,
			    start_time,
			    match_url,
			    scraped_at,
			    scrape_errors,
			    s3_folder_key,
			    file,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES (
			    #{e.matchId},
			    CAST(NULLIF(#{e.matchDate}, '') AS DATE),
			    #{e.homeTeamName},
			    #{e.awayTeamName},
			    #{e.category},
			    #{e.homeScore},
			    #{e.awayScore},
			    #{e.status},
			    #{e.startTime},
			    #{e.matchUrl},
			    #{e.scrapedAt},
			    #{scrapeErrors},
			    #{folderKey},
			    #{e.file},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insertMatch(@Param("e") MatchDetailMatchEntity e, @Param("scrapeErrors") String scrapeErrors,
			@Param("folderKey") String folderKey);

	/**
	 * 試合詳細: マッチモメンタム（1分1行）: まとめて登録（row_no = リストの順番 + 1）
	 * @param list 登録対象（1 試合分）
	 * @return 登録件数
	 */
	@Insert("""
			<script>
			INSERT INTO match_detail_momentum (
			    match_id,
			    row_no,
			    match_date,
			    home_team_name,
			    away_team_name,
			    half,
			    half_seq,
			    minute_label,
			    minute,
			    added_minute,
			    side,
			    team_name,
			    momentum_value,
			    signed_momentum_value,
			    file,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES
			<foreach collection="list" item="e" index="idx" separator=",">
			(
			    #{e.matchId},
			    #{idx} + 1,
			    CAST(NULLIF(#{e.matchDate}, '') AS DATE),
			    #{e.homeTeamName},
			    #{e.awayTeamName},
			    #{e.half},
			    #{e.halfSeq},
			    #{e.minuteLabel},
			    #{e.minute},
			    #{e.addedMinute},
			    #{e.side},
			    #{e.teamName},
			    #{e.momentumValue},
			    #{e.signedMomentumValue},
			    #{e.file},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			</foreach>
			</script>
			""")
	int insertMomentum(@Param("list") List<MatchDetailMomentumEntity> list);

	/**
	 * 試合詳細: モメンタム上のアイコン: まとめて登録（row_no = リストの順番 + 1）
	 * @param list 登録対象（1 試合分）
	 * @return 登録件数
	 */
	@Insert("""
			<script>
			INSERT INTO match_detail_momentum_incident (
			    match_id,
			    row_no,
			    match_date,
			    home_team_name,
			    away_team_name,
			    seq,
			    incident_type,
			    minute_label,
			    minute,
			    added_minute,
			    side,
			    team_name,
			    file,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES
			<foreach collection="list" item="e" index="idx" separator=",">
			(
			    #{e.matchId},
			    #{idx} + 1,
			    CAST(NULLIF(#{e.matchDate}, '') AS DATE),
			    #{e.homeTeamName},
			    #{e.awayTeamName},
			    #{e.seq},
			    #{e.incidentType},
			    #{e.minuteLabel},
			    #{e.minute},
			    #{e.addedMinute},
			    #{e.side},
			    #{e.teamName},
			    #{e.file},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			</foreach>
			</script>
			""")
	int insertMomentumIncident(@Param("list") List<MatchDetailMomentumIncidentEntity> list);

	/**
	 * 試合詳細: サマリーの出来事: まとめて登録（row_no = リストの順番 + 1）
	 * @param list 登録対象（1 試合分）
	 * @return 登録件数
	 */
	@Insert("""
			<script>
			INSERT INTO match_detail_summary (
			    match_id,
			    row_no,
			    match_date,
			    home_team_name,
			    away_team_name,
			    half,
			    half_score,
			    half_seq,
			    time_label,
			    minute,
			    added_minute,
			    side,
			    team_name,
			    incident_type,
			    player_name,
			    player_url,
			    sub_out_player_name,
			    sub_out_player_url,
			    score_at_time,
			    note,
			    note2,
			    description,
			    file,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES
			<foreach collection="list" item="e" index="idx" separator=",">
			(
			    #{e.matchId},
			    #{idx} + 1,
			    CAST(NULLIF(#{e.matchDate}, '') AS DATE),
			    #{e.homeTeamName},
			    #{e.awayTeamName},
			    #{e.half},
			    #{e.halfScore},
			    #{e.halfSeq},
			    #{e.timeLabel},
			    #{e.minute},
			    #{e.addedMinute},
			    #{e.side},
			    #{e.teamName},
			    #{e.incidentType},
			    #{e.playerName},
			    #{e.playerUrl},
			    #{e.subOutPlayerName},
			    #{e.subOutPlayerUrl},
			    #{e.scoreAtTime},
			    #{e.note},
			    #{e.note2},
			    #{e.description},
			    #{e.file},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			</foreach>
			</script>
			""")
	int insertSummary(@Param("list") List<MatchDetailSummaryEntity> list);

	/**
	 * 試合詳細: ゴール: まとめて登録（row_no = リストの順番 + 1）
	 * @param list 登録対象（1 試合分）
	 * @return 登録件数
	 */
	@Insert("""
			<script>
			INSERT INTO match_detail_goal (
			    match_id,
			    row_no,
			    match_date,
			    home_team_name,
			    away_team_name,
			    time_label,
			    minute,
			    added_minute,
			    half,
			    scoring_side,
			    scoring_team_name,
			    scorer_name,
			    scorer_team_name,
			    own_goal_flg,
			    penalty_flg,
			    assist_name,
			    score_at_time,
			    incident_type,
			    note,
			    player_url,
			    file,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES
			<foreach collection="list" item="e" index="idx" separator=",">
			(
			    #{e.matchId},
			    #{idx} + 1,
			    CAST(NULLIF(#{e.matchDate}, '') AS DATE),
			    #{e.homeTeamName},
			    #{e.awayTeamName},
			    #{e.timeLabel},
			    #{e.minute},
			    #{e.addedMinute},
			    #{e.half},
			    #{e.scoringSide},
			    #{e.scoringTeamName},
			    #{e.scorerName},
			    #{e.scorerTeamName},
			    #{e.ownGoalFlg},
			    #{e.penaltyFlg},
			    #{e.assistName},
			    #{e.scoreAtTime},
			    #{e.incidentType},
			    #{e.note},
			    #{e.playerUrl},
			    #{e.file},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			</foreach>
			</script>
			""")
	int insertGoal(@Param("list") List<MatchDetailGoalEntity> list);

	/**
	 * 試合詳細: フォーメーション: まとめて登録（row_no = リストの順番 + 1）
	 * @param list 登録対象（1 試合分）
	 * @return 登録件数
	 */
	@Insert("""
			<script>
			INSERT INTO match_detail_formation (
			    match_id,
			    row_no,
			    match_date,
			    home_team_name,
			    away_team_name,
			    side,
			    team_name,
			    formation,
			    average_rating,
			    coach_name,
			    coach_nationality,
			    starter_count,
			    played_count,
			    file,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES
			<foreach collection="list" item="e" index="idx" separator=",">
			(
			    #{e.matchId},
			    #{idx} + 1,
			    CAST(NULLIF(#{e.matchDate}, '') AS DATE),
			    #{e.homeTeamName},
			    #{e.awayTeamName},
			    #{e.side},
			    #{e.teamName},
			    #{e.formation},
			    #{e.averageRating},
			    #{e.coachName},
			    #{e.coachNationality},
			    #{e.starterCount},
			    #{e.playedCount},
			    #{e.file},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			</foreach>
			</script>
			""")
	int insertFormation(@Param("list") List<MatchDetailFormationEntity> list);

	/**
	 * 試合詳細: ラインアップ: まとめて登録（row_no = リストの順番 + 1）
	 * @param list 登録対象（1 試合分）
	 * @return 登録件数
	 */
	@Insert("""
			<script>
			INSERT INTO match_detail_lineup (
			    match_id,
			    row_no,
			    match_date,
			    home_team_name,
			    away_team_name,
			    side,
			    team_name,
			    lineup_type,
			    player_id,
			    jersey_number,
			    player_name,
			    nationality,
			    role,
			    played_flg,
			    sub_in_minute,
			    sub_in_for_player_name,
			    sub_out_minute,
			    sub_out_for_player_name,
			    final_rating,
			    pitch_row,
			    pitch_col,
			    goal_count,
			    own_goal_count,
			    events,
			    file,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES
			<foreach collection="list" item="e" index="idx" separator=",">
			(
			    #{e.matchId},
			    #{idx} + 1,
			    CAST(NULLIF(#{e.matchDate}, '') AS DATE),
			    #{e.homeTeamName},
			    #{e.awayTeamName},
			    #{e.side},
			    #{e.teamName},
			    #{e.lineupType},
			    #{e.playerId},
			    #{e.jerseyNumber},
			    #{e.playerName},
			    #{e.nationality},
			    #{e.role},
			    #{e.playedFlg},
			    #{e.subInMinute},
			    #{e.subInForPlayerName},
			    #{e.subOutMinute},
			    #{e.subOutForPlayerName},
			    #{e.finalRating},
			    #{e.pitchRow},
			    #{e.pitchCol},
			    #{e.goalCount},
			    #{e.ownGoalCount},
			    #{e.events},
			    #{e.file},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			</foreach>
			</script>
			""")
	int insertLineup(@Param("list") List<MatchDetailLineupEntity> list);

	/**
	 * 試合詳細: 欠場選手: まとめて登録（row_no = リストの順番 + 1）
	 * @param list 登録対象（1 試合分）
	 * @return 登録件数
	 */
	@Insert("""
			<script>
			INSERT INTO match_detail_missing (
			    match_id,
			    row_no,
			    match_date,
			    home_team_name,
			    away_team_name,
			    side,
			    team_name,
			    player_name,
			    nationality,
			    reason,
			    player_url,
			    file,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES
			<foreach collection="list" item="e" index="idx" separator=",">
			(
			    #{e.matchId},
			    #{idx} + 1,
			    CAST(NULLIF(#{e.matchDate}, '') AS DATE),
			    #{e.homeTeamName},
			    #{e.awayTeamName},
			    #{e.side},
			    #{e.teamName},
			    #{e.playerName},
			    #{e.nationality},
			    #{e.reason},
			    #{e.playerUrl},
			    #{e.file},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			</foreach>
			</script>
			""")
	int insertMissing(@Param("list") List<MatchDetailMissingEntity> list);

	/**
	 * 試合詳細: 選手スタッツ（全タブまとめ）: まとめて登録（row_no = リストの順番 + 1）
	 * @param list 登録対象（1 試合分）
	 * @return 登録件数
	 */
	@Insert("""
			<script>
			INSERT INTO match_detail_member_stat (
			    match_id,
			    row_no,
			    match_date,
			    home_team_name,
			    away_team_name,
			    player_id,
			    player_name,
			    position,
			    side,
			    team_name,
			    fs_rating,
			    shots_total,
			    expected_goals,
			    passes_accurate,
			    touches_total,
			    touches_box_opposite,
			    dribbles_won,
			    duels_total,
			    goals,
			    expected_goals_on_target,
			    shots_on_target,
			    shots_off_target,
			    shots_blocked,
			    shots_box_in,
			    shots_box_out,
			    shots_head,
			    big_chances_missed,
			    fouls_suffered,
			    offsides,
			    big_chances_created,
			    key_passes,
			    assists_goal,
			    expected_assists,
			    passes_final_third_accurate,
			    long_balls_accurate,
			    crosses_accurate,
			    duels_won,
			    duels_aerial_won,
			    duels_ground_won,
			    tackles_won,
			    fouls_committed,
			    interceptions,
			    clearances,
			    errors_lead_to_goal,
			    errors_lead_to_shot,
			    match_minutes_played,
			    goals_own,
			    cards_yellow,
			    cards_red,
			    saves_total,
			    goals_conceded,
			    goals_prevented,
			    expected_goals_on_target_faced,
			    punches_total,
			    keeper_throws_total,
			    keeper_sweeper_total,
			    extra_stats,
			    file,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES
			<foreach collection="list" item="e" index="idx" separator=",">
			(
			    #{e.matchId},
			    #{idx} + 1,
			    CAST(NULLIF(#{e.matchDate}, '') AS DATE),
			    #{e.homeTeamName},
			    #{e.awayTeamName},
			    #{e.playerId},
			    #{e.playerName},
			    #{e.position},
			    #{e.side},
			    #{e.teamName},
			    #{e.fsRating},
			    #{e.shotsTotal},
			    #{e.expectedGoals},
			    #{e.passesAccurate},
			    #{e.touchesTotal},
			    #{e.touchesBoxOpposite},
			    #{e.dribblesWon},
			    #{e.duelsTotal},
			    #{e.goals},
			    #{e.expectedGoalsOnTarget},
			    #{e.shotsOnTarget},
			    #{e.shotsOffTarget},
			    #{e.shotsBlocked},
			    #{e.shotsBoxIn},
			    #{e.shotsBoxOut},
			    #{e.shotsHead},
			    #{e.bigChancesMissed},
			    #{e.foulsSuffered},
			    #{e.offsides},
			    #{e.bigChancesCreated},
			    #{e.keyPasses},
			    #{e.assistsGoal},
			    #{e.expectedAssists},
			    #{e.passesFinalThirdAccurate},
			    #{e.longBallsAccurate},
			    #{e.crossesAccurate},
			    #{e.duelsWon},
			    #{e.duelsAerialWon},
			    #{e.duelsGroundWon},
			    #{e.tacklesWon},
			    #{e.foulsCommitted},
			    #{e.interceptions},
			    #{e.clearances},
			    #{e.errorsLeadToGoal},
			    #{e.errorsLeadToShot},
			    #{e.matchMinutesPlayed},
			    #{e.goalsOwn},
			    #{e.cardsYellow},
			    #{e.cardsRed},
			    #{e.savesTotal},
			    #{e.goalsConceded},
			    #{e.goalsPrevented},
			    #{e.expectedGoalsOnTargetFaced},
			    #{e.punchesTotal},
			    #{e.keeperThrowsTotal},
			    #{e.keeperSweeperTotal},
			    #{e.extraStats},
			    #{e.file},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			</foreach>
			</script>
			""")
	int insertMemberStat(@Param("list") List<MatchDetailMemberStatEntity> list);

}
