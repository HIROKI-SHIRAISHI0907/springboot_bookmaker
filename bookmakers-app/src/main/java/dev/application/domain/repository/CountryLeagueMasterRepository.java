package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import dev.common.entity.CountryLeagueMasterEntity;

@Mapper
public interface CountryLeagueMasterRepository {

	@Insert("""
			    INSERT INTO country_league_master (
			    	id,
			        country,
			        league,
			        team,
			        link,
			        register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			    	#{id},
			        #{country},
			        #{league},
			        #{team},
			        #{link},
			        #{registerId},
			        #{registerTime},
			        #{updateId},
			        #{updateTime}
			    )
			""")
	int insert(CountryLeagueMasterEntity entity);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        team,
			        link
			    FROM
			    	country_league_master
			    WHERE
			        country = #{country} AND
			        league = #{league} AND
			        team = #{team};
			""")
	List<CountryLeagueMasterEntity> findByCountryLeague(String country, String league, String team);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        team
			    FROM
			    	country_league_master;
			""")
	List<CountryLeagueMasterEntity> findData();

}