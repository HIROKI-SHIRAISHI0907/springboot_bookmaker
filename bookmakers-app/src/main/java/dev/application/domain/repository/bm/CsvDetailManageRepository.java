package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.CsvDetailManageEntity;



@Mapper
public interface CsvDetailManageRepository {

	@Select("""
			SELECT
				*
			FROM
				csv_detail_manage
			WHERE
			    data_category = #{dataCategory}
			AND
			    season = #{season}
			AND
			    home_team_name = #{homeTeamName}
			AND
			    away_team_name = #{awayTeamName}
			AND
				check_fin_flg = '1'
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
			SET check_fin_flg = #{checkFinFlg},
			    update_time = CURRENT_TIMESTAMP,
			    update_id = 'ADMIN'
			WHERE
			    data_category = #{dataCategory}
			AND
			    season = #{season}
			AND
			    home_team_name = #{homeTeamName}
			AND
			    away_team_name = #{awayTeamName}
			""")
	int update(@Param("dataCategory") String dataCategory,
			@Param("season") String season,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("checkFinFlg") String checkFinFlg);

}
