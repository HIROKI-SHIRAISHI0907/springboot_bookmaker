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
 * 対象グループ単位で処理する版。
 * - data の生行を seq 範囲でページングしない
 * - まず対象グループ一覧を取得
 * - グループごとに seq 一覧を取得
 * - その seq 一覧で詳細データを取得
 */
@Mapper
public interface BookCsvDataRepository {

    /**
     * CSV対象グループ件数取得.
     *
     * ここで数えるのは data の生行数ではなく、
     * 「home_team_name + away_team_name + times」単位の対象グループ件数。
     */
    @Select("""
            SELECT COUNT(*)
            FROM (
              SELECT
                d.home_team_name,
                d.away_team_name,
                d.times
              FROM data d
              WHERE
                EXISTS (
                  SELECT 1
                  FROM data x
                  WHERE x.home_team_name = d.home_team_name
                    AND x.away_team_name = d.away_team_name
                    AND x.times IN ('ハーフタイム', '第一ハーフ')
                )
                AND EXISTS (
                  SELECT 1
                  FROM data y
                  WHERE y.home_team_name = d.home_team_name
                    AND y.away_team_name = d.away_team_name
                    AND (
                      y.times IN ('終了済', '第二ハーフ')
                      OR REPLACE(BTRIM(y.times), ' ', '') LIKE '%ペナルティ%'
                    )
                )
              GROUP BY d.home_team_name, d.away_team_name, d.times
            ) t
            """)
    int countGroupTargets();

    /**
     * CSV対象グループ一覧をページング取得.
     *
     * 注意:
     * - seq範囲で data を切るのではない
     * - まず全体から対象グループを確定し、その結果一覧を LIMIT/OFFSET でページングする
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
                  SELECT 1
                  FROM data x
                  WHERE x.home_team_name = d.home_team_name
                    AND x.away_team_name = d.away_team_name
                    AND x.times IN ('ハーフタイム', '第一ハーフ')
                )
                AND EXISTS (
                  SELECT 1
                  FROM data y
                  WHERE y.home_team_name = d.home_team_name
                    AND y.away_team_name = d.away_team_name
                    AND (
                      y.times IN ('終了済', '第二ハーフ')
                      OR REPLACE(BTRIM(y.times), ' ', '') LIKE '%ペナルティ%'
                    )
                )
              GROUP BY d.home_team_name, d.away_team_name, d.times
            ) t
            ORDER BY t.homeTeamName, t.awayTeamName, t.times, t.seq
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<SeqWithKey> findGroupTargetsPage(@Param("limit") int limit,
                                          @Param("offset") int offset);

    /**
     * 対象グループに属する seq 一覧を取得.
     *
     * 優先順位:
     * 1) home_team_name + away_team_name + match_id
     * 2) home_team_name + away_team_name + data_category
     * 3) home_team_name + away_team_name
     *
     * ※ matchId / dataCategory は引数で受けず、
     *   同一 home/away 内で代表値をSQL側で決定する。
     */
    @Select("""
            WITH base AS (
              SELECT
                seq,
                home_team_name,
                away_team_name,
                match_id,
                data_category
              FROM data
              WHERE home_team_name = #{homeTeamName}
                AND away_team_name = #{awayTeamName}
            ),
            best_match AS (
              SELECT
                match_id
              FROM base
              WHERE match_id IS NOT NULL
                AND BTRIM(match_id) <> ''
              GROUP BY match_id
              ORDER BY COUNT(*) DESC, MIN(seq) ASC, match_id ASC
              LIMIT 1
            ),
            best_category AS (
              SELECT
                data_category
              FROM base
              WHERE data_category IS NOT NULL
                AND BTRIM(data_category) <> ''
              GROUP BY data_category
              ORDER BY COUNT(*) DESC, MIN(seq) ASC, data_category ASC
              LIMIT 1
            ),
            prioritized AS (
              SELECT DISTINCT
                seq,
                CASE
                  WHEN EXISTS (
                    SELECT 1
                    FROM best_match bm
                    WHERE bm.match_id = base.match_id
                  ) THEN 1
                  WHEN NOT EXISTS (SELECT 1 FROM best_match)
                       AND EXISTS (
                         SELECT 1
                         FROM best_category bc
                         WHERE bc.data_category = base.data_category
                       ) THEN 2
                  ELSE 3
                END AS priority
              FROM base
            )
            SELECT
              seq
            FROM prioritized
            WHERE priority = (
              SELECT MIN(priority)
              FROM prioritized
            )
            ORDER BY seq ASC
            """)
    List<Integer> findSeqListByGroup(@Param("homeTeamName") String homeTeamName,
                                     @Param("awayTeamName") String awayTeamName);

    /**
     * seq 指定で詳細データ取得.
     *
     * 既存仕様を維持。
     * ただし呼び出し元では「全体一括」ではなく、
     * グループ単位で使うこと。
     */
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
