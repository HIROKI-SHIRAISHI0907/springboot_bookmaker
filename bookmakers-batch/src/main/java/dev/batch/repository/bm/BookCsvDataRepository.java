package dev.batch.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.batch.bm_b011.SeqWithKey;
import dev.common.entity.DataEntity;

/**
 * CSV出力用のデータ取得リポジトリ.
 *
 * MyBatis(@Mapper)版を NamedParameterJdbcTemplate 版へ移植。
 *
 * @author shiraishitoshio
 */
@Mapper
public interface BookCsvDataRepository {

	/**
	 * CSV作成用検索データ
	 */

	@Select("""
			  SELECT
				  t.dataCategory,
				  t.homeTeamName,
				  t.awayTeamName,
				  t.times,
				  t.seq
				FROM (
				  SELECT
				    d.home_team_name AS homeTeamName,
				    d.away_team_name AS awayTeamName,
				    d.times          AS times,

				    COALESCE(
				      MIN(CASE WHEN d.data_category LIKE '%ラウンド%' THEN d.seq END),
				      MIN(d.seq)
				    ) AS seq,

				    COALESCE(
				      MAX(CASE WHEN d.data_category LIKE '%ラウンド%' THEN d.data_category END),
				      MAX(d.data_category)
				    ) AS dataCategory

				  FROM data d
				  WHERE
				    EXISTS (
				      SELECT 1 FROM data x
				      WHERE x.home_team_name = d.home_team_name
				        AND x.away_team_name = d.away_team_name
				        AND x.times IN ('ハーフタイム', '第一ハーフ')
				    )
				    -- ★ここだけ変更：終了済/第二ハーフ が無くても 90分台なら対象にする
				    AND EXISTS (
				      SELECT 1 FROM data y
				      WHERE y.home_team_name = d.home_team_name
				        AND y.away_team_name = d.away_team_name
				        AND (
				          y.times IN ('終了済', '第二ハーフ')
				          OR REPLACE(BTRIM(y.times), ' ', '') LIKE '%ペナルティ%'
				        )
				    )
				  GROUP BY
				    d.home_team_name, d.away_team_name, d.times
				) t
				ORDER BY t.homeTeamName, t.awayTeamName, t.seq ASC
			""")
	List<SeqWithKey> findAllSeqsWithKey();

	@Select("""
			<script>
			SELECT DISTINCT
			  seq                               AS seq,
			  condition_result_data_seq_id      AS conditionResultDataSeqId,
			  data_category                     AS dataCategory,
			  times                             AS times,
			  home_rank                         AS homeRank,
			  home_team_name                    AS homeTeamName,
			  home_score                        AS homeScore,
			  away_rank                         AS awayRank,
			  away_team_name                    AS awayTeamName,
			  away_score                        AS awayScore,
			  home_exp                          AS homeExp,
			  away_exp                          AS awayExp,
			  home_in_goal_exp                  AS homeInGoalExp,
			  away_in_goal_exp                  AS awayInGoalExp,
			  home_donation                     AS homeDonation,
			  away_donation                     AS awayDonation,
			  home_shoot_all                    AS homeShootAll,
			  away_shoot_all                    AS awayShootAll,
			  home_shoot_in                     AS homeShootIn,
			  away_shoot_in                     AS awayShootIn,
			  home_shoot_out                    AS homeShootOut,
			  away_shoot_out                    AS awayShootOut,
			  home_block_shoot                  AS homeBlockShoot,
			  away_block_shoot                  AS awayBlockShoot,
			  home_big_chance                   AS homeBigChance,
			  away_big_chance                   AS awayBigChance,
			  home_corner                       AS homeCorner,
			  away_corner                       AS awayCorner,
			  home_box_shoot_in                 AS homeBoxShootIn,
			  away_box_shoot_in                 AS awayBoxShootIn,
			  home_box_shoot_out                AS homeBoxShootOut,
			  away_box_shoot_out                AS awayBoxShootOut,
			  home_goal_post                    AS homeGoalPost,
			  away_goal_post                    AS awayGoalPost,
			  home_goal_head                    AS homeGoalHead,
			  away_goal_head                    AS awayGoalHead,
			  home_keeper_save                  AS homeKeeperSave,
			  away_keeper_save                  AS awayKeeperSave,
			  home_free_kick                    AS homeFreeKick,
			  away_free_kick                    AS awayFreeKick,
			  home_offside                      AS homeOffside,
			  away_offside                      AS awayOffside,
			  home_foul                         AS homeFoul,
			  away_foul                         AS awayFoul,
			  home_yellow_card                  AS homeYellowCard,
			  away_yellow_card                  AS awayYellowCard,
			  home_red_card                     AS homeRedCard,
			  away_red_card                     AS awayRedCard,
			  home_slow_in                      AS homeSlowIn,
			  away_slow_in                      AS awaySlowIn,
			  home_box_touch                    AS homeBoxTouch,
			  away_box_touch                    AS awayBoxTouch,
			  home_pass_count                   AS homePassCount,
			  away_pass_count                   AS awayPassCount,
			  home_long_pass_count              AS homeLongPassCount,
			  away_long_pass_count              AS awayLongPassCount,
			  home_final_third_pass_count       AS homeFinalThirdPassCount,
			  away_final_third_pass_count       AS awayFinalThirdPassCount,
			  home_cross_count                  AS homeCrossCount,
			  away_cross_count                  AS awayCrossCount,
			  home_tackle_count                 AS homeTackleCount,
			  away_tackle_count                 AS awayTackleCount,
			  home_clear_count                  AS homeClearCount,
			  away_clear_count                  AS awayClearCount,
			  home_duel_count                   AS homeDuelCount,
			  away_duel_count                   AS awayDuelCount,
			  home_intercept_count              AS homeInterceptCount,
			  away_intercept_count              AS awayInterceptCount,
			  record_time                       AS recordTime,
			  weather                           AS weather,
			  temparature                       AS temparature,
			  humid                             AS humid,
			  judge_member                      AS judgeMember,
			  home_manager                      AS homeManager,
			  away_manager                      AS awayManager,
			  home_formation                    AS homeFormation,
			  away_formation                    AS awayFormation,
			  studium                           AS studium,
			  capacity                          AS capacity,
			  audience                          AS audience,
			  location                          AS location,
			  home_max_getting_scorer           AS homeMaxGettingScorer,
			  away_max_getting_scorer           AS awayMaxGettingScorer,
			  home_max_getting_scorer_game_situation AS homeMaxGettingScorerGameSituation,
			  away_max_getting_scorer_game_situation AS awayMaxGettingScorerGameSituation,
			  home_team_home_score              AS homeTeamHomeScore,
			  home_team_home_lost               AS homeTeamHomeLost,
			  away_team_home_score              AS awayTeamHomeScore,
			  away_team_home_lost               AS awayTeamHomeLost,
			  home_team_away_score              AS homeTeamAwayScore,
			  home_team_away_lost               AS homeTeamAwayLost,
			  away_team_away_score              AS awayTeamAwayScore,
			  away_team_away_lost               AS awayTeamAwayLost,
			  notice_flg                        AS noticeFlg,
			  game_link                         AS gameLink,
			  goal_time                         AS goalTime,
			  goal_team_member                  AS goalTeamMember,
			  judge                             AS judge,
			  home_team_style                   AS homeTeamStyle,
			  away_team_style                   AS awayTeamStyle,
			  probablity                        AS probablity,
			  prediction_score_time             AS predictionScoreTime,
			  game_id                           AS gameId,
			  match_id                          AS matchId,
			  time_sort_seconds                 AS timeSortSeconds,
			  add_manual_flg                    AS addManualFlg
			FROM data
			<where>
			  <choose>
			    <when test="seqList != null and seqList.size() > 0">
			      seq IN
			      <foreach collection="seqList" item="item" open="(" separator="," close=")">
			        #{item}
			      </foreach>
			    </when>
			    <otherwise>
			      1 = 0
			    </otherwise>
			  </choose>
			</where>
			ORDER BY record_time ASC
			</script>
			""")
			List<DataEntity> findByData(@Param("seqList") List<Integer> seqList);

}
