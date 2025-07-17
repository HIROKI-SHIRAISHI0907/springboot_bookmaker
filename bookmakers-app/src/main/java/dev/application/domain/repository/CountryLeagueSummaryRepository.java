package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m006.CountryLeagueSummaryEntity;

@Mapper
public interface CountryLeagueSummaryRepository {

    @Insert("""
        INSERT INTO type_of_country_league_data (
            country,
            league,
            data_count,
            csv_count,
            register_id,
            register_time,
            update_id,
            update_time
        ) VALUES (
            #{country},
            #{league},
            #{dataCount},
            #{csvCount},
            #{registerId},
            #{registerTime},
            #{updateId},
            #{updateTime}
        )
    """)
    void insert(CountryLeagueSummaryEntity entity);
}
