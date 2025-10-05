package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.FutureEntity;

@Mapper
public interface FutureMasterRepository {

	@Insert("""
			    INSERT INTO future_master (
			        game_team_category,
			        future_time,
			        home_rank,
			        away_rank,
			        home_team_name,
			        away_team_name,
					home_max_getting_scorer,
					away_max_getting_scorer,
					home_team_home_score,
					home_team_home_lost,
					away_team_home_score,
					away_team_home_lost,
					home_team_away_score,
					home_team_away_lost,
					away_team_away_score,
					away_team_away_lost,
					game_link,
					data_time,
					start_flg,
					register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			        #{gameTeamCategory},
			        CAST(#{futureTime} AS timestamptz),
			        #{homeRank},
			        #{awayRank},
			        #{homeTeamName},
			        #{awayTeamName},
			        #{homeMaxGettingScorer},
			        #{awayMaxGettingScorer},
			        #{homeTeamHomeScore},
			        #{homeTeamHomeLost},
			        #{awayTeamHomeScore},
			        #{awayTeamHomeLost},
			        #{homeTeamAwayScore},
			        #{homeTeamAwayLost},
			        #{awayTeamAwayScore},
			        #{awayTeamAwayLost},
			        #{gameLink},
			        CAST(#{dataTime} AS timestamptz),
			        1,
			        #{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz)
			    );
			""")
	int insert(FutureEntity entity);

	@Select("""
			    SELECT
			        COUNT(*)
			    FROM
			    	future_master
			    WHERE
			        game_team_category = #{gameTeamCategory} AND
			        home_team_name = #{homeTeamName} AND
			        away_team_name = #{awayTeamName};
			""")
	int findDataCount(FutureEntity entity);

	@Update("""
			    UPDATE
			        future_master
			    SET
			    	start_flg = #{startFlg}
			    WHERE
			        seq = CAST(#{seq,jdbcType=VARCHAR} AS INTEGER);
			""")
	int updateStartFlg(String seq, String startFlg);

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
