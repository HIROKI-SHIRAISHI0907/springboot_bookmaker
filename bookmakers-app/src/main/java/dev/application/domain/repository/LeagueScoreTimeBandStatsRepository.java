package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m007_bm_m016.TimeRangeFeatureStatsEachLeagueEntity;


@Mapper
public interface LeagueScoreTimeBandStatsRepository {

    @Insert("""
        INSERT INTO within_data (
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
    void insert(TimeRangeFeatureStatsEachLeagueEntity entity);
}
