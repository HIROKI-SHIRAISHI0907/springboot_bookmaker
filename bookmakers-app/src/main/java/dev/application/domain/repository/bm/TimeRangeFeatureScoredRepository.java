package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m007_bm_m016.TimeRangeFeatureScoredEntity;

@Mapper
public interface TimeRangeFeatureScoredRepository {

	@Insert("""
			    INSERT INTO #{tableName} (
			        country,
			        league,
			        timeRange,
			        feature,
			        threshold,
			        target,
			        search,
			        ratio,
			        register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			        #{timeRange},
			        #{feature},
			        #{thresHold},
			        #{target},
			        #{search},
			        #{ratio},
			        #{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz)
			    )
			""")
	int insert(TimeRangeFeatureScoredEntity entity);

	@Select("""
			    SELECT
			        id
			        target,
			        search
			    FROM
			    	#{tableName}
			    WHERE
			    	country = #{country} AND
			        league = #{league} AND
			        time_range = #{timeRange} AND
			        feature = #{feature} AND
			        threshold = #{thresHold};
			    )
			""")
	List<TimeRangeFeatureScoredEntity> findData(@Param("country") String country, @Param("league") String league,
			@Param("timeRange") String timeRange, @Param("feature") String feature,@Param("thresHold") String thresHold,@Param("tableName") String tableName);
}
