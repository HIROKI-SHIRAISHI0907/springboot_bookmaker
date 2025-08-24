package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.CountryLeagueSeasonMasterEntity;

@Mapper
public interface CountryLeagueSeasonMasterRepository {

	@Insert({
			"INSERT INTO country_league_season_master (",
			"id, country, league, start_season_date, end_season_date, path, upd_stamp,",
			"register_id, register_time, update_id, update_time) VALUES (",
			"#{id}, #{country}, #{league}, #{startSeasonDate}, #{endSeasonDate}, #{path}, #{updStamp}",
			"#{registerId}, #{registerTime}, #{updateId}, #{updateTime});"
	})
	int insert(CountryLeagueSeasonMasterEntity entity);

	@Update({
			"UPDATE country_league_season_master SET",
			"country = #{country},",
			"league = #{league},",
			"start_season_date = #{startSeasonDate},",
			"end_season_date = #{endSeasonDate},",
			"upd_stamp = #{updStamp} ",
			"WHERE id = #{id}"
	})
	int update(CountryLeagueSeasonMasterEntity entity);

	@Select({
			"SELECT id, country, league, start_season_date, end_season_date, path, upd_stamp "
					+ "FROM country_league_season_master ",
			"WHERE country = #{country} AND league = #{league}"
	})
	List<CountryLeagueSeasonMasterEntity> findByCountryAndLeague(String country, String league);

	@Select("""
			    SELECT
			        COUNT(*)
			    FROM
			    	country_league_season_master
			    WHERE
			        country = #{country} AND
			        league = #{league};
			""")
	int findDataCount(CountryLeagueSeasonMasterEntity entity);
}
