package dev.mng.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.common.entity.DataEntity;
import dev.mng.csvmng.SeqWithKey;

@Mapper
public interface BookCsvDataRepository {

	@Select("""
			SELECT
			  d.data_category   AS dataCategory,
			  d.home_team_name  AS homeTeamName,
			  d.away_team_name  AS awayTeamName,
			  d.times           AS times,
			  d.seq             AS seq
			FROM data d
			ORDER BY d.data_category, d.home_team_name, d.away_team_name, d.times, d.seq
			""")
	List<SeqWithKey> findAllSeqsWithKey();

	@Select({
	  "<script>",
	  "SELECT DISTINCT",
	  "  seq,",
	  "  condition_result_data_seq_id,",
	  "  data_category,",
	  "  times,",
	  "  home_rank,",
	  "  home_team_name,",
	  "  home_score,",
	  "  away_rank,",
	  "  away_team_name,",
	  "  away_score,",
	  "  home_exp,",
	  "  away_exp,",
	  "  home_donation,",
	  "  away_donation,",
	  "  home_shoot_all,",
	  "  away_shoot_all,",
	  "  home_shoot_in,",
	  "  away_shoot_in,",
	  "  home_shoot_out,",
	  "  away_shoot_out,",
	  "  home_block_shoot,",
	  "  away_block_shoot,",
	  "  home_big_chance,",
	  "  away_big_chance,",
	  "  home_corner,",
	  "  away_corner,",
	  "  home_box_shoot_in,",
	  "  away_box_shoot_in,",
	  "  home_box_shoot_out,",
	  "  away_box_shoot_out,",
	  "  home_goal_post,",
	  "  away_goal_post,",
	  "  home_goal_head,",
	  "  away_goal_head,",
	  "  home_keeper_save,",
	  "  away_keeper_save,",
	  "  home_free_kick,",
	  "  away_free_kick,",
	  "  home_offside,",
	  "  away_offside,",
	  "  home_foul,",
	  "  away_foul,",
	  "  home_yellow_card,",
	  "  away_yellow_card,",
	  "  home_red_card,",
	  "  away_red_card,",
	  "  home_slow_in,",
	  "  away_slow_in,",
	  "  home_box_touch,",
	  "  away_box_touch,",
	  "  home_pass_count,",
	  "  away_pass_count,",
	  "  home_final_third_pass_count,",
	  "  away_final_third_pass_count,",
	  "  home_cross_count,",
	  "  away_cross_count,",
	  "  home_tackle_count,",
	  "  away_tackle_count,",
	  "  home_clear_count,",
	  "  away_clear_count,",
	  "  home_intercept_count,",
	  "  away_intercept_count,",
	  "  record_time,",
	  "  weather,",
	  "  temparature,",
	  "  humid,",
	  "  judge_member,",
	  "  home_manager,",
	  "  away_manager,",
	  "  home_formation,",
	  "  away_formation,",
	  "  studium,",
	  "  capacity,",
	  "  audience,",
	  "  home_max_getting_scorer,",
	  "  away_max_getting_scorer,",
	  "  home_max_getting_scorer_game_situation,",
	  "  away_max_getting_scorer_game_situation,",
	  "  home_team_home_score,",
	  "  home_team_home_lost,",
	  "  away_team_home_score,",
	  "  away_team_home_lost,",
	  "  home_team_away_score,",
	  "  home_team_away_lost,",
	  "  away_team_away_score,",
	  "  away_team_away_lost,",
	  "  notice_flg,",
	  "  goal_time,",
	  "  goal_team_member,",
	  "  judge,",
	  "  home_team_style,",
	  "  away_team_style,",
	  "  probablity,",
	  "  prediction_score_time",
	  "FROM data",
	  "WHERE seq IN",
	  "  <foreach item='id' collection='seqList' open='(' separator=',' close=')'>",
	  "    #{id}",
	  "  </foreach>",
	  "</script>"
	})
	List<DataEntity> findBySeq(@Param("seqList") List<Integer> seqList);

}
