package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
	int insert(CountryLeagueSummaryEntity entity);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        data_count,
			        csv_count
			    FROM
			    	type_of_country_league_data
			    WHERE
			        country = #{country} AND
			        league = #{league};
			""")
	List<CountryLeagueSummaryEntity> findByCountryLeague(String country, String league);

	@Update("""
			    UPDATE type_of_country_league_data
			    SET
			        country = #{country},
			        league = #{league},
			        data_count = #{dataCount},
			        csv_count = #{csvCount}
			    WHERE
			        id = #{id};
			""")
	int update(CountryLeagueSummaryEntity entity);
}
