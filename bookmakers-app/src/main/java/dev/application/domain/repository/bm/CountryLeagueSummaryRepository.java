package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m006.CountryLeagueSummaryEntity;

@Mapper
public interface CountryLeagueSummaryRepository {

	@Insert("""
			    INSERT INTO country_league_summary (
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
			        #{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz)
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
			    	country_league_summary
			    WHERE
			        country = #{country} AND
			        league = #{league};
			""")
	List<CountryLeagueSummaryEntity> findByCountryLeague(String country, String league);

	@Update("""
			    UPDATE country_league_summary
			    SET
			        country = #{country},
			        league = #{league},
			        data_count = #{dataCount},
			        csv_count = #{csvCount}
			    WHERE
			        id = CAST(#{id,jdbcType=VARCHAR} AS INTEGER);
			""")
	int update(CountryLeagueSummaryEntity entity);
}
