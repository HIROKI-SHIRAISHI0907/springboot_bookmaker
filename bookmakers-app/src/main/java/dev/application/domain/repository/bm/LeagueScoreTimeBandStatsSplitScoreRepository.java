package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m017_bm_m018.LeagueScoreTimeBandStatsSplitScoreEntity;

@Mapper
public interface LeagueScoreTimeBandStatsSplitScoreRepository {

	@Insert("""
			    INSERT INTO league_score_time_band_stats_split_score (
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
			        #{country},
			        #{league},
			        #{homeScoreValue},
			        #{awayScoreValue},
			        #{homeTimeRangeArea},
			        #{awayTimeRangeArea},
			        #{target},
			        #{search},
			        #{ratio},
			        #{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz)
			    );
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
	List<LeagueScoreTimeBandStatsSplitScoreEntity> findData(@Param("country") String country,@Param("league") String league,
			@Param("homeScoreValue") String homeScoreValue, @Param("awayScoreValue") String awayScoreValue, @Param("homeTimeRange") String homeTimeRange,@Param("awayTimeRange") String awayTimeRange);

	@Update("""
		    UPDATE league_score_time_band_stats_split_score
		    SET
		        target = #{target},
		        search = #{search}
		    WHERE
		        id = CAST(#{id,jdbcType=VARCHAR} AS INTEGER)
		""")
	int update(@Param("id") String id,@Param("target") String target,@Param("search") String search);
}
