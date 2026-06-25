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
			     	season_year
			    FROM
			    	country_league_season_master
			    WHERE
				    country = #{country}
				    AND league = #{league}
				    AND del_flg = '0';
			""")
	String findSeasonYear(@Param("country") String country,
			@Param("league") String league);

	@Select("""
			    SELECT
			        country,
			        league,
			        end_season_date AS endSeasonDate
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

	@Select("""
			    SELECT
			        country,
			        league,
			        end_season_date AS endSeasonDate
			    FROM
			        country_league_season_master
			    WHERE
			        del_flg = '0'
			""")
	List<CountryLeagueSeasonMasterEntity> findDateList();

	@Select("""
			    SELECT
			        country,
			        league,
			        end_season_date AS endSeasonDate
			    FROM
			        country_league_season_master
			    WHERE
			        country = #{country}
			        AND league = #{league}
			        AND del_flg = '0'
			""")
	List<CountryLeagueSeasonMasterEntity> findWhereData(@Param("country") String country,
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
			"           'SYSTEM',",
			"			CURRENT_TIMESTAMP,",
			"			'SYSTEM',",
			"			CURRENT_TIMESTAMP)",
			"" })
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

	@Select("""
			    SELECT
			        id,
			        country,
			        league,
			        season_year AS seasonYear,
			        start_season_date AS startSeasonDate,
			        end_season_date AS endSeasonDate,
			        round,
			        path,
			        icon,
			        valid_flg AS validFlg,
			        del_flg AS delFlg
			    FROM country_league_season_master
			    WHERE country = #{country}
			      AND league = #{league}
			      AND del_flg = '0'
			    ORDER BY id DESC
			    LIMIT 1
			""")
	CountryLeagueSeasonMasterEntity findLatestByCountryLeague(
			@Param("country") String country,
			@Param("league") String league);

	/**
	 * ID指定更新
	 *
	 * 仕様:
	 * - Service 側で merge 済みデータが渡される前提
	 * - 空/null/N/A で既存値を壊さないロジックは Service 側で実施
	 */
	@Update("""
			UPDATE country_league_season_master
			   SET country = #{country},
			       league = #{league},
			       season_year = #{seasonYear},

			       start_season_date = CASE
			           WHEN #{startSeasonDate} IS NULL THEN start_season_date
			           WHEN BTRIM(#{startSeasonDate}) = '' THEN start_season_date
			           WHEN LOWER(BTRIM(#{startSeasonDate})) = 'n/a' THEN start_season_date
			           WHEN LOWER(BTRIM(#{startSeasonDate})) = 'null' THEN start_season_date
			           WHEN BTRIM(#{startSeasonDate}) = '-' THEN start_season_date
			           WHEN BTRIM(#{startSeasonDate}) = '未定' THEN start_season_date
			           ELSE CAST(#{startSeasonDate} AS timestamptz)
			       END,

			       end_season_date = CASE
			           WHEN #{endSeasonDate} IS NULL THEN end_season_date
			           WHEN BTRIM(#{endSeasonDate}) = '' THEN end_season_date
			           WHEN LOWER(BTRIM(#{endSeasonDate})) = 'n/a' THEN end_season_date
			           WHEN LOWER(BTRIM(#{endSeasonDate})) = 'null' THEN end_season_date
			           WHEN BTRIM(#{endSeasonDate}) = '-' THEN end_season_date
			           WHEN BTRIM(#{endSeasonDate}) = '未定' THEN end_season_date
			           ELSE CAST(#{endSeasonDate} AS timestamptz)
			       END,

			       round = CASE
			           WHEN #{round} IS NULL THEN round
			           WHEN BTRIM(#{round}) = '' THEN round
			           WHEN LOWER(BTRIM(#{round})) = 'n/a' THEN round
			           WHEN LOWER(BTRIM(#{round})) = 'null' THEN round
			           WHEN BTRIM(#{round}) = '-' THEN round
			           WHEN BTRIM(#{round}) = '未定' THEN round
			           ELSE #{round}
			       END,

			       path = CASE
			           WHEN #{path} IS NULL THEN path
			           WHEN BTRIM(#{path}) = '' THEN path
			           WHEN LOWER(BTRIM(#{path})) = 'n/a' THEN path
			           WHEN LOWER(BTRIM(#{path})) = 'null' THEN path
			           WHEN BTRIM(#{path}) = '-' THEN path
			           WHEN BTRIM(#{path}) = '未定' THEN path
			           ELSE #{path}
			       END,

			       icon = CASE
			           WHEN #{icon} IS NULL THEN icon
			           WHEN BTRIM(#{icon}) = '' THEN icon
			           WHEN LOWER(BTRIM(#{icon})) = 'n/a' THEN icon
			           WHEN LOWER(BTRIM(#{icon})) = 'null' THEN icon
			           WHEN BTRIM(#{icon}) = '-' THEN icon
			           WHEN BTRIM(#{icon}) = '未定' THEN icon
			           ELSE #{icon}
			       END,

			       valid_flg = COALESCE(NULLIF(#{validFlg}, ''), valid_flg),
			       del_flg = COALESCE(NULLIF(#{delFlg}, ''), del_flg),
			       update_id = 'SYSTEM',
			       update_time = CURRENT_TIMESTAMP
			 WHERE id = CAST(#{id} AS BIGINT)
			""")
	int updateById(CountryLeagueSeasonMasterEntity entity);

}
