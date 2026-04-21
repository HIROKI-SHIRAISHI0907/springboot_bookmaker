package dev.batch.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.CsvDetailManageEntity;

@Mapper
public interface BookCsvDetailManageRepository {

	@Select("""
			SELECT
				csv_id         AS csvId,
				data_category  AS dataCategory,
				season         AS season,
				home_team_name AS homeTeamName,
				away_team_name AS awayTeamName,
				check_fin_flg  AS checkFinFlg
			FROM
				csv_detail_manage
			WHERE
				data_category = #{dataCategory}
			AND season = #{season}
			AND home_team_name = #{homeTeamName}
			AND away_team_name = #{awayTeamName}
			""")
	CsvDetailManageEntity select(CsvDetailManageEntity entity);

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
			""")
	int insert(CsvDetailManageEntity entity);

	@Update("""
			UPDATE csv_detail_manage
			SET
			    csv_id = #{csvId},
			    check_fin_flg = #{checkFinFlg},
			    update_time = CURRENT_TIMESTAMP,
			    update_id = 'ADMIN'
			WHERE
				data_category = #{dataCategory}
			AND season = #{season}
			AND home_team_name = #{homeTeamName}
			AND away_team_name = #{awayTeamName}
			""")
	int update(CsvDetailManageEntity entity);

}
