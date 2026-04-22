package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.common.entity.CsvDetailManageEntity;

@Mapper
public interface CsvDetailManageRepository {

	/**
	 * check_fin_flg='1' の既存件数
	 */
	@Select("""
			SELECT COUNT(*)
			FROM csv_detail_manage
			WHERE data_category = #{dataCategory}
			  AND season = #{season}
			  AND home_team_name = #{homeTeamName}
			  AND away_team_name = #{awayTeamName}
			  AND check_fin_flg = '1'
			""")
	int existsCheckedFinCount(
			@Param("dataCategory") String dataCategory,
			@Param("season") String season,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	/**
	 * data_category 単位で check_fin_flg='1' の既存行をまとめて取得
	 * CoreStat 側のN+1回避用
	 */
	@Select({
		"<script>",
		"SELECT",
		"    csv_id AS csvId,",
		"    data_category AS dataCategory,",
		"    season AS season,",
		"    home_team_name AS homeTeamName,",
		"    away_team_name AS awayTeamName,",
		"    check_fin_flg AS checkFinFlg",
		"FROM csv_detail_manage",
		"WHERE check_fin_flg = '1'",
		"  AND data_category IN",
		"  <foreach collection='dataCategories' item='dataCategory' open='(' separator=',' close=')'>",
		"    #{dataCategory}",
		"  </foreach>",
		"</script>"
	})
	List<CsvDetailManageEntity> selectCheckedFinByDataCategories(
			@Param("dataCategories") List<String> dataCategories);

	/**
	 * manual用UPSERT
	 * UNIQUE(data_category, season, home_team_name, away_team_name) 前提
	 */
	@Insert("""
			INSERT INTO csv_detail_manage (
			    csv_id,
			    data_category,
			    season,
			    home_team_name,
			    away_team_name,
			    check_fin_flg,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES (
			    #{csvId},
			    #{dataCategory},
			    #{season},
			    #{homeTeamName},
			    #{awayTeamName},
			    #{checkFinFlg},
			    'ADMIN',
			    CURRENT_TIMESTAMP,
			    'ADMIN',
			    CURRENT_TIMESTAMP
			)
			ON CONFLICT (data_category, season, home_team_name, away_team_name)
			DO UPDATE SET
			    csv_id = EXCLUDED.csv_id,
			    check_fin_flg = EXCLUDED.check_fin_flg,
			    update_id = 'ADMIN',
			    update_time = CURRENT_TIMESTAMP
			""")
	int upsert(CsvDetailManageEntity entity);
}
