package dev.application.domain.repository.bm;

import java.util.Collections;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m097.AnalyzeManualEntity;
import dev.common.entity.DataEntity;

@Mapper
public interface AnalyzeManualRepository {

	/**
	 * 指定した matchId 一覧に該当する analyze_manual_data を取得
	 *
	 * ※ 空リスト対策のため、呼び出し側はこの default メソッドを使う
	 */
	default List<AnalyzeManualEntity> selectByMatchIds(List<String> matchIds) {
		if (matchIds == null || matchIds.isEmpty()) {
			return Collections.emptyList();
		}
		return selectByMatchIdsInternal(matchIds);
	}

	/**
	 * 実SQL実行用
	 */
	@Select({
		"<script>",
		"SELECT",
		"    id,",
		"    game_category AS gameCategory,",
		"    times,",
		"    home_team_name AS homeTeamName,",
		"    away_team_name AS awayTeamName,",
		"    match_id AS matchId,",
		"    register_id AS registerId,",
		"    register_time AS registerTime,",
		"    update_id AS updateId,",
		"    update_time AS updateTime",
		"FROM analyze_manual_data",
		"WHERE match_id IN",
		"<foreach item='matchId' collection='matchIds' open='(' separator=',' close=')'>",
		"    #{matchId}",
		"</foreach>",
		"</script>"
	})
	List<AnalyzeManualEntity> selectByMatchIdsInternal(@Param("matchIds") List<String> matchIds);

	/**
	 * add_manual_flg = '1' かつ 終了済み かつ
	 * analyze_manual_data に未登録のデータのみ取得
	 */
	@Select("""
			SELECT d.*
			FROM data d
			WHERE d.add_manual_flg = '1'
			  AND d.times = '終了済'
			  AND NOT EXISTS (
			      SELECT 1
			      FROM analyze_manual_data a
			      WHERE a.game_category  = d.data_category
			        AND a.times          = d.times
			        AND a.home_team_name = d.home_team_name
			        AND a.away_team_name = d.away_team_name
			        AND a.match_id       = d.match_id
			  )
			ORDER BY d.record_time ASC NULLS LAST, d.seq ASC
			""")
	List<DataEntity> selectNotAnalyzedManualFinishedData();

	/**
	 * 分析済み管理テーブルへ登録
	 */
	@Insert("""
			INSERT INTO analyze_manual_data (
			    game_category,
			    times,
			    home_team_name,
			    away_team_name,
			    match_id,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES (
			    #{gameCategory},
			    #{times},
			    #{homeTeamName},
			    #{awayTeamName},
			    #{matchId},
			    #{registerId},
			    CURRENT_TIMESTAMP,
			    #{updateId},
			    CURRENT_TIMESTAMP
			)
			""")
	int insertAnalyzeManualData(AnalyzeManualEntity entity);

	/**
	 * 念のため個別存在確認したい場合用
	 */
	@Select("""
			SELECT COUNT(*)
			FROM analyze_manual_data
			WHERE game_category  = #{gameCategory}
			  AND times          = #{times}
			  AND home_team_name = #{homeTeamName}
			  AND away_team_name = #{awayTeamName}
			  AND match_id       = #{matchId}
			""")
	int countAnalyzeManualData(AnalyzeManualEntity entity);
}
