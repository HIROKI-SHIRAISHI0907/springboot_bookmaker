package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

	@Update("""
			    UPDATE #{tableName}
			    SET
			        target = #{target},
			        search = #{search};
			    WHERE
			    	id = #{id};
			""")
	int update(String id, String target, String search, String tableName);

	@Select("""
			    SELECT
			        id
			        target,
			        search
			    FROM
			    	#{tableName}
			    WHERE
			        time_range = #{timeRange},
			        feature = #{feature};
			    )
			""")
	List<TimeRangeFeatureAllLeagueEntity> findData(String timeRange, String feature, String tableName);
}
