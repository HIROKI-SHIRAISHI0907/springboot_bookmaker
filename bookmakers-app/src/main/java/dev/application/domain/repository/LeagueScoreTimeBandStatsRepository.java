package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m017_bm_m018.LeagueScoreTimeBandStatsEntity;

@Mapper
public interface LeagueScoreTimeBandStatsRepository {

	@Insert("""
			    INSERT INTO league_score_time_band_stats (
			        id,
			        country,
			        league,
			        sum_score_value,
			        time_range_area,
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
			        #{sumScoreValue},
			        #{timeRangeArea},
			        #{target},
			        #{search},
			        #{ratio},
			        #{registerId},
			        #{registerTime},
			        #{updateId},
			        #{updateTime}
			    )
			""")
	int insert(LeagueScoreTimeBandStatsEntity entity);

	@Select("""
			    SELECT
			        id,
			        target,
			        search,
			        time_range_area
			    FROM
			    	league_score_time_band_stats
			    WHERE
			        country = #{country} AND
			        league = #{league} AND
			        sum_score_value = #{sumScoreValue} AND
			        time_range_area = #{timeRange};
			""")
	List<LeagueScoreTimeBandStatsEntity> findData(String country, String league,
			String sumScoreValue, String timeRange);

	@Update("""
		    UPDATE league_score_time_band_stats
		    SET
		        target = #{target},
		        search = #{search}
		    WHERE
		        id = #{id};
		""")
	int update(String id, String target, String search);
}
