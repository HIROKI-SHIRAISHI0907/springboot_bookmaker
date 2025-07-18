package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m007_bm_m016.TimeRangeFeatureScoredEntity;


@Mapper
public interface TimeRangeFeatureScoredRepository {

    @Insert("""
        INSERT INTO within_data (
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
    void insert(TimeRangeFeatureScoredEntity entity);
}
