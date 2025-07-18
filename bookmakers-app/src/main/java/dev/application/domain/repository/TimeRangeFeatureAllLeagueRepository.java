package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m007_bm_m016.TimeRangeFeatureAllLeagueEntity;


@Mapper
public interface TimeRangeFeatureAllLeagueRepository {

    @Insert("""
        INSERT INTO within_data (
            id,
            country,
            league,
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
    void insert(TimeRangeFeatureAllLeagueEntity entity);
}
