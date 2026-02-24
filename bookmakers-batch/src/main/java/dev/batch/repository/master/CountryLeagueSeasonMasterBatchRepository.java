package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.CountryLeagueSeasonMasterEntity;

@Mapper
public interface CountryLeagueSeasonMasterBatchRepository {

	@Select("""
			    SELECT
			        country,
			        league,
			        end_season_date
			    FROM
			        country_league_season_master
			    WHERE
			        end_season_date < CAST(#{date} AS DATE) AND
			        del_flg = '0'
			""")
	List<CountryLeagueSeasonMasterEntity> findExpiredByEndDate(@Param("date") String date);

	@Select("""
			    SELECT
			        country,
			        league
			    FROM
			        country_league_season_master
			    WHERE
			        end_season_date IS NULL AND
			        del_flg = '0'
			""")
	List<CountryLeagueSeasonMasterEntity> findHyphen();

	@Update("""
			    UPDATE country_league_season_master
			    SET
			        end_season_date = NULL,
			        del_flg = '0',
			        update_time = CURRENT_TIMESTAMP,
			        update_id = 'BATCH'
			    WHERE
			        country = #{country}
			        AND league = #{league}
			""")
	int clearEndSeasonDate(@Param("country") String country,
			@Param("league") String league);

	@Insert({
		"	    INSERT INTO country_league_season_master (",
		"	        country,",
		"	        league,",
		"	        season_year,",
		"	        start_season_date,",
		"	        end_season_date,",
		"	        round,",
		"	        path,",
		"	        icon,",
		"	        valid_flg,",
		"           del_flg,",
		"	        register_id,",
		"	        register_time,",
		"	        update_id,",
		"	        update_time",
		"	    ) VALUES (",
		"	        #{country},",
		"	        #{league},",
		"	        #{seasonYear},",
		"	        #{startSeasonDate}::timestamptz,",
		"			#{endSeasonDate}::timestamptz,",
		"	        #{round},",
		"	        #{path},",
		"	        #{icon},",
		"	        '0',",
		"	        '0',",
		"           #{registerId},",
		"			CURRENT_TIMESTAMP",
		"			{updateId},",
		"			CURRENT_TIMESTAMP)",
	""})
	int insert(CountryLeagueSeasonMasterEntity entity);

	@Select("""
			    SELECT
			        COUNT(*)
			    FROM
			    	country_league_season_master
			    WHERE
			        country = #{country} AND
			        league = #{league} AND
			        del_flg = '0';
			""")
	int findDataCount(CountryLeagueSeasonMasterEntity entity);

	@Select("""
			    SELECT DISTINCT
			        country,
			        league
			    FROM
			        country_league_season_master
			    WHERE
			    	country = #{country} AND
			        league = #{league} AND
			        start_season_date IS NOT NULL
			        AND CURRENT_DATE BETWEEN (start_season_date::date - INTERVAL '10 days') AND start_season_date::date
			""")
	List<CountryLeagueSeasonMasterEntity> findCountryLeagueStartingWithin10Days(
			@Param("country") String country,
			@Param("league") String league);

	@Select("""
			    SELECT
			     country,
			     league,
			     path,
			     season_year,
			     start_season_date,
			     end_season_date,
			     round,
			     icon,
			     valid_flg,
			     del_flg
			 FROM country_league_season_master
			 WHERE country = #{country} AND league = #{league}
			""")
	List<CountryLeagueSeasonMasterEntity> findByCountryAndLeague(@Param("country") String country,
			@Param("league") String league);

	@Select("""
			    SELECT
			        country,
			        league,
			        path,
			     	season_year,
			     	start_season_date,
			     	end_season_date,
			     	round,
			     	icon,
			     	valid_flg,
			     	del_flg
			    FROM
			    	country_league_season_master
			    WHERE
			        country = #{country} AND
			        path = #{path};
			""")
	List<CountryLeagueSeasonMasterEntity> findByCountryAndPath(@Param("country") String country,
			@Param("path") String path);

	@Update({
		"	    UPDATE ",
		"	        country_league_season_master ",
		"	    SET ",
		"	        season_year = #{seasonYear},",
		"			start_season_date = #{startSeasonDate}::timestamptz,",
		"			end_season_date = #{endSeasonDate}::timestamptz,",
		//"			start_season_date = #{startSeasonDate},",
		//"			end_season_date = #{endSeasonDate},",
		"			round = #{round},",
		"			path = #{path},",
		"			icon = #{icon},",
		"			valid_flg = '0',",
		"			del_flg = '0'",
		"	    WHERE",
		"	        country = #{country} AND",
		"	        league = #{league};"
	})
	int updateByCountryLeague(CountryLeagueSeasonMasterEntity entity);

	@Update("""
			 UPDATE country_league_season_master
			 SET
			 	 valid_flg = '0',
			     del_flg = '1'
			 WHERE
			     country = #{country}
			     AND league = #{league}
			     AND path = #{path}
			""")
	int logicalDeleteByCountryLeaguePath(@Param("country") String country,
			@Param("league") String league, @Param("path") String path);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        path,
			     	season_year,
			     	start_season_date,
			     	end_season_date,
			     	round,
			     	icon,
			     	valid_flg,
			     	del_flg
			    FROM
			    	country_league_season_master
			    ORDER BY
			    	id;
			""")
	List<CountryLeagueSeasonMasterEntity> findData();

}
