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

	/**
	 * BM_M097 用:
	 * future_master にある future_time の JST日付一覧を取得
	 */
	@Select("""
			SELECT
			    seq,
			    home_team_name AS homeTeamName,
			    away_team_name AS awayTeamName,
			    future_time    AS futureTime
			FROM future_master
			WHERE future_time IS NOT NULL
			  AND home_team_name IS NOT NULL
			  AND away_team_name IS NOT NULL
			ORDER BY seq
			""")
	List<FutureEntity> findFutureDatesForManualStat();

}
