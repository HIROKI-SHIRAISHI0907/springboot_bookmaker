package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m017_bm_m018.LeagueScoreTimeBandStatsSplitScoreEntity;

@Mapper
public interface LeagueScoreTimeBandStatsSplitScoreRepository {

	@Insert("""
			    INSERT INTO league_score_time_band_stats_split_score (
			        id,
			        country,
			        league,
			        home_score_value,
			        away_score_value,
			        home_time_range_area,
			        away_time_range_area,
			        target,
			        search,
			        ratio,
			        register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			        #{id},
			        #{country},
			        #{league},
			        #{homeScoreValue},
			        #{awayScoreValue},
			        #{homeTimeRangeArea},
			        #{awayTimeRangeArea},
			        #{target},
			        #{search},
			        #{ratio},
			        #{registerId},
			        #{registerTime},
			        #{updateId},
			        #{updateTime}
			    )
			""")
	int insert(LeagueScoreTimeBandStatsSplitScoreEntity entity);

	@Select("""
			    SELECT
			        id,
			        target,
			        search,
			        home_time_range_area,
			        away_time_range_area
			    FROM
			    	league_score_time_band_stats_split_score
			    WHERE
			        country = #{country} AND
			        league = #{league} AND
			        home_score_value = #{homeScoreValue} AND
			        away_score_value = #{awayScoreValue} AND
			        home_time_range_area = #{homeTimeRange} AND
			        away_time_range_area = #{awayTimeRange};
			""")
	List<LeagueScoreTimeBandStatsSplitScoreEntity> findData(String country, String league,
			String homeScoreValue, String awayScoreValue, String homeTimeRange, String awayTimeRange);

	@Update("""
		    UPDATE league_score_time_band_stats_split_score
		    SET
		        target = #{target},
		        search = #{search}
		    WHERE
		        id = #{id};
		""")
	int update(String id, String target, String search);
}
