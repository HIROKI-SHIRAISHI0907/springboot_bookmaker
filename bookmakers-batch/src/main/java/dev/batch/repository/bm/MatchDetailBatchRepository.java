package dev.batch.repository.bm;

import java.util.LinkedHashMap;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;

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
	 * 試合詳細の取り直し候補（2026-11-22 追加。B012 が毎日 b016_game_detail_path_&lt;連番&gt;.json に足す）
	 * <p>試合日が from〜to の登録済みの試合のうち、必ずあるはずのデータ（サマリー・フォーメーション・ラインアップ・選手スタッツ）が
	 * 無く、かつ同じリーグの直近 50 試合で 1 割以上の試合にはそのデータがあるもの（＝リーグとしては提供されている）。
	 * 例: J2・J3 の選手スタッツは試合の翌日にはまだタブが無いことがあるので、2〜7 日後に取り直す。
	 * リーグで提供されていないデータ（J2 のモメンタム等）・ゴール 0-0・欠場者なしでは取り直さない
	 * （web の DetailAvailabilityService と同じ判定）。リーグの試合が 5 試合未満なら判断しない（取り直さない）。</p>
	 *
	 * @param from 試合日（から）
	 * @param to   試合日（まで）
	 * @return match_id・match_url・match_date（yyyy-MM-dd）
	 */
	@Select("""
			WITH x AS (
			  SELECT match_id, match_url, match_date, league,
			         ROW_NUMBER() OVER (PARTITION BY league ORDER BY match_date DESC NULLS LAST, scraped_at DESC NULLS LAST) AS rn
			  FROM (
			    SELECT match_id, match_url, match_date, scraped_at,
			           CASE WHEN strpos(COALESCE(category, ''), ' - ') > 0
			                THEN btrim(substr(category, 1, strpos(category, ' - ') - 1))
			                ELSE btrim(COALESCE(category, '')) END AS league
			    FROM match_detail_match
			  ) y
			),
			h AS (
			  SELECT x.*,
			         EXISTS (SELECT 1 FROM match_detail_summary t     WHERE t.match_id = x.match_id) AS has_summary,
			         EXISTS (SELECT 1 FROM match_detail_formation t   WHERE t.match_id = x.match_id) AS has_formation,
			         EXISTS (SELECT 1 FROM match_detail_lineup t      WHERE t.match_id = x.match_id) AS has_lineup,
			         EXISTS (SELECT 1 FROM match_detail_member_stat t WHERE t.match_id = x.match_id) AS has_member_stat
			  FROM x
			  WHERE x.rn <= 50 OR x.match_date BETWEEN #{from} AND #{to}
			),
			lg AS (
			  SELECT league, COUNT(*) AS total,
			         COUNT(*) FILTER (WHERE has_summary)     AS n_summary,
			         COUNT(*) FILTER (WHERE has_formation)   AS n_formation,
			         COUNT(*) FILTER (WHERE has_lineup)      AS n_lineup,
			         COUNT(*) FILTER (WHERE has_member_stat) AS n_member_stat
			  FROM h WHERE rn <= 50
			  GROUP BY league
			)
			SELECT h.match_id AS "matchId", h.match_url AS "matchUrl", to_char(h.match_date, 'YYYY-MM-DD') AS "matchDate"
			FROM h JOIN lg ON lg.league = h.league
			WHERE h.match_date BETWEEN #{from} AND #{to}
			  AND COALESCE(h.match_url, '') <> ''
			  AND lg.total >= 5
			  AND (   (NOT h.has_summary     AND lg.n_summary     >= lg.total * 0.1)
			       OR (NOT h.has_formation   AND lg.n_formation   >= lg.total * 0.1)
			       OR (NOT h.has_lineup      AND lg.n_lineup      >= lg.total * 0.1)
			       OR (NOT h.has_member_stat AND lg.n_member_stat >= lg.total * 0.1))
			ORDER BY h.match_date, h.match_id
			""")
	List<java.util.Map<String, Object>> findRetryDetailTargets(@Param("from") java.time.LocalDate from,
			@Param("to") java.time.LocalDate to);

	/**
	 * 試合詳細（統計詳細データ）: 国・リーグ の試合を 9 テーブルからまとめて削除する（B013 シーズン終了処理用。2026-11 追加）
	 * <p>対象の試合ID（match_id）は次のどちらかで決める。1 文で消すので、途中で失敗しても一部だけ消えることはない。</p>
	 * <ul>
	 *   <li>static_data のうち data_category が「国: リーグ」または「国: リーグ - ...」の行の match_id</li>
	 *   <li>match_detail_match のうち category が「国: リーグ」「国 / リーグ」（前にパンくずが付く「... / 国 / リーグ」も）で、
	 *       そのまま終わるか「 - ...」（ラウンドなど）が続くもの</li>
	 * </ul>
	 * 引数は LIKE 用にエスケープ済み（\ % _）の値を渡す（{@code EachTableTransaction#execute}）。
	 * static_data を消す前に呼ぶこと（EachTableTransaction の最初のループ）。
	 *
	 * @param colon     「国: リーグ」（エスケープなし。= で比べる）
	 * @param colonLike 「国: リーグ」（エスケープ済み）
	 * @param slash     「国 / リーグ」（エスケープなし）
	 * @param slashLike 「国 / リーグ」（エスケープ済み）
	 * @return 削除した試合数（match_detail_match の行数）
	 */
	@Delete("""
			WITH m AS (
			    SELECT match_id FROM static_data
			     WHERE match_id IS NOT NULL AND match_id <> ''
			       AND (data_category = #{colon} OR data_category LIKE #{colonLike} || ' - %')
			    UNION
			    SELECT match_id FROM match_detail_match
			     WHERE category = #{colon} OR category LIKE #{colonLike} || ' - %'
			        OR category = #{slash} OR category LIKE #{slashLike} || ' - %'
			        OR category LIKE '% / ' || #{slashLike} OR category LIKE '% / ' || #{slashLike} || ' - %'
			),
			d1 AS (DELETE FROM match_detail_member_stat       WHERE match_id IN (SELECT match_id FROM m)),
			d2 AS (DELETE FROM match_detail_missing           WHERE match_id IN (SELECT match_id FROM m)),
			d3 AS (DELETE FROM match_detail_lineup            WHERE match_id IN (SELECT match_id FROM m)),
			d4 AS (DELETE FROM match_detail_formation         WHERE match_id IN (SELECT match_id FROM m)),
			d5 AS (DELETE FROM match_detail_goal              WHERE match_id IN (SELECT match_id FROM m)),
			d6 AS (DELETE FROM match_detail_summary           WHERE match_id IN (SELECT match_id FROM m)),
			d7 AS (DELETE FROM match_detail_momentum_incident WHERE match_id IN (SELECT match_id FROM m)),
			d8 AS (DELETE FROM match_detail_momentum          WHERE match_id IN (SELECT match_id FROM m))
			DELETE FROM match_detail_match WHERE match_id IN (SELECT match_id FROM m)
			""")
	int deleteByLeague(@Param("colon") String colon, @Param("colonLike") String colonLike,
			@Param("slash") String slash, @Param("slashLike") String slashLike);

	/**
	 * 試合詳細（統計詳細データ）: 国・リーグの試合で、match_detail_match に登録済みの試合ID
	 * （B013 シーズン終了時のバックアップ用。対象の決め方は {@link #deleteByLeague} と同じ。2026-11-18 追加）
	 */
	@Select("""
			WITH m AS (
			    SELECT match_id FROM static_data
			     WHERE match_id IS NOT NULL AND match_id <> ''
			       AND (data_category = #{colon} OR data_category LIKE #{colonLike} || ' - %')
			    UNION
			    SELECT match_id FROM match_detail_match
			     WHERE category = #{colon} OR category LIKE #{colonLike} || ' - %'
			        OR category = #{slash} OR category LIKE #{slashLike} || ' - %'
			        OR category LIKE '% / ' || #{slashLike} OR category LIKE '% / ' || #{slashLike} || ' - %'
			)
			SELECT match_id FROM match_detail_match WHERE match_id IN (SELECT match_id FROM m) ORDER BY match_id
			""")
	List<String> findMatchIdsByLeague(@Param("colon") String colon, @Param("colonLike") String colonLike,
			@Param("slash") String slash, @Param("slashLike") String slashLike);

	/**
	 * テーブルの列名（定義順）。バックアップ CSV の見出しに使う（2026-11-18 追加）
	 * @param table テーブル名
	 */
	@Select("""
			SELECT column_name FROM information_schema.columns
			 WHERE table_schema = current_schema() AND table_name = #{table}
			 ORDER BY ordinal_position
			""")
	List<String> findColumns(@Param("table") String table);

	/**
	 * 試合詳細（統計詳細データ）: 国・リーグの試合の行を 1 行ずつ handler に渡す（B013 シーズン終了時のバックアップ用。2026-11-18 追加）
	 * <p>対象の決め方は {@link #deleteByLeague} と同じ。table は呼び出し側で 9 テーブルのどれかに限ること（${} で埋め込むため）。</p>
	 */
	@Select("""
			WITH m AS (
			    SELECT match_id FROM static_data
			     WHERE match_id IS NOT NULL AND match_id <> ''
			       AND (data_category = #{colon} OR data_category LIKE #{colonLike} || ' - %')
			    UNION
			    SELECT match_id FROM match_detail_match
			     WHERE category = #{colon} OR category LIKE #{colonLike} || ' - %'
			        OR category = #{slash} OR category LIKE #{slashLike} || ' - %'
			        OR category LIKE '% / ' || #{slashLike} OR category LIKE '% / ' || #{slashLike} || ' - %'
			)
			SELECT t.* FROM ${table} t WHERE t.match_id IN (SELECT match_id FROM m) ORDER BY t.match_id
			""")
	@Options(fetchSize = 1000, resultSetType = ResultSetType.FORWARD_ONLY)
	void selectByLeague(@Param("table") String table,
			@Param("colon") String colon, @Param("colonLike") String colonLike,
			@Param("slash") String slash, @Param("slashLike") String slashLike,
			ResultHandler<LinkedHashMap<String, Object>> handler);

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
