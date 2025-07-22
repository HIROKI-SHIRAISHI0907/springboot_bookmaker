package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import dev.common.entity.FutureEntity;

@Mapper
public interface FutureRepository {

	@Insert("""
			    INSERT INTO future (
			        seq,
			        game_team_category,
			        future_time,
			        home_rank,
			        away_rank,
			        home_team_name,
			        home_score,
			        away_team_name,
			        away_score,
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
					register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			        #{seq},
			        #{gameTeamCategory},
			        #{futureTime},
			        #{homeRank},
			        #{awayRank},
			        #{homeTeamName},
			        #{homeScore},
			        #{awayTeamName},
			        #{awayScore},
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
			        #{dataTime},
			        #{registerId},
			       #{registerTime},
			       #{updateId},
			       #{updateTime}
			    )
			""")
	int insert(FutureEntity entity);

	@Select("""
			    SELECT
			        COUNT(*)
			    FROM
			    	future
			    WHERE
			        game_team_category = #{gameTeamCategory} AND
			        home_team_name = #{homeTeamName} AND
			        away_team_name = #{awayTeamName};
			""")
	int findDataCount(FutureEntity entity);

}
