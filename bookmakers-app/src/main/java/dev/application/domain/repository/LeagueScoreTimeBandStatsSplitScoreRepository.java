package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m007_bm_m016.TimeRangeFeatureStatsEachLeagueEntity;


@Mapper
public interface LeagueScoreTimeBandStatsSplitScoreRepository {

    @Insert("""
        INSERT INTO within_xminutes_data (
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
    void insert(TimeRangeFeatureStatsEachLeagueEntity entity);
}
