package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m007_bm_m016.TimeRangeFeatureAllLeagueEntity;

@Mapper
public interface TimeRangeFeatureAllLeagueRepository {

	@Insert("""
			    INSERT INTO #{tableName} (
			        id,
			        timeRange,
			        feature,
			        thresHold,
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
			        #{timeRange},
			        #{feature},
			        #{thresHold},
			        #{target},
			        #{search},
			        #{ratio},
			        #{registerId},
			        #{registerTime},
			        #{updateId},
			        #{updateTime}
			    )
			""")
	int insert(TimeRangeFeatureAllLeagueEntity entity);

	@Select("""
			    SELECT
			        id
			        target,
			        search
			    FROM
			    	#{tableName}
			    WHERE
			        time_range = #{timeRange} AND
			        feature = #{feature} AND
			        threshold = #{thresHold};
			    )
			""")
	List<TimeRangeFeatureAllLeagueEntity> findData(String timeRange, String feature,
			String thresHold, String tableName);
}
