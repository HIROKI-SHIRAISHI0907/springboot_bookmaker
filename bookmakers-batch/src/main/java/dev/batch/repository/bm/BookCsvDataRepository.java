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
				  seq,
				  condition_result_data_seq_id,
				  data_category AS dataCategory,
				  times,
				  home_rank,
				  home_team_name AS homeTeamName,
				  home_score AS homeScore,
				  away_rank,
				  away_team_name AS awayTeamName,
				  away_score AS awayScore,
				  home_exp,
				  away_exp,
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
				  record_time AS recordTime,
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
				  goal_time,
				  goal_team_member,
				  judge,
				  home_team_style,
				  away_team_style,
				  probablity,
				  prediction_score_time
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
