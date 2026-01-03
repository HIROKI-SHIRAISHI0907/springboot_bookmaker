package dev.application.domain.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.FutureEntity;

@Mapper
public interface FutureMasterRepository {

	@Update("""
			  UPDATE future_master
			  SET start_flg = #{startFlg}
			  WHERE seq = CAST(#{seq} AS INTEGER)
			""")
	int updateStartFlg(@Param("seq") String seq, @Param("startFlg") String startFlg);

	@Select("""
			    SELECT
			        seq
			    FROM
			    	future_master
			    WHERE
			        home_team_name = #{homeTeamName} AND
			        away_team_name = #{awayTeamName};
			""")
	List<FutureEntity> findOnlyTeam(FutureEntity entity);

	@Update("""
			UPDATE future_master
			SET start_flg = #{startFlg}
			WHERE future_time < now()
			""")
	int updateFutureTimeFlg(@Param("startFlg") String startFlg);

	@Select("""
			    SELECT
			        COUNT(*)
			    FROM
			    	future_master;
			""")
	int findAll();

}
