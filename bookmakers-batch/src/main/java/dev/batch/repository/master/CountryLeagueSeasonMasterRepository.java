package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.CountryLeagueSeasonMasterEntity;

@Mapper
public interface CountryLeagueSeasonMasterRepository {

	@Select("""
			    SELECT
			        country,
			        league,
			        end_season_date
			    FROM
			        country_league_season_master
			    WHERE
			        end_season_date < CAST(#{date} AS DATE)
			""")
	List<CountryLeagueSeasonMasterEntity> findExpiredByEndDate(String date);

	@Update("""
			    UPDATE country_league_season_master
			    SET
			        end_season_date = NULL,
			        update_time = CURRENT_TIMESTAMP,
			        update_id = 'BATCH'
			    WHERE
			        country = #{country}
			        AND league = #{league}
			""")
	int clearEndSeasonDate(@Param("country") String country,
			@Param("league") String league);

	@Insert("""
			    INSERT INTO country_league_season_master (
			        country,
			        league,
			        start_season_date,
			        end_season_date,
			        round,
			        path,
			        icon,
			        valid_flg,
			        register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			        #{country},
			        #{league},
			        #{startSeasonDate}::timestamptz,
					#{endSeasonDate}::timestamptz,
			        #{round},
			        #{path},
			        #{icon},
			        #{validFlg},
			        'TEST',
			        CURRENT_TIMESTAMP,
			        'TEST',
			        CURRENT_TIMESTAMP
			    )
			""")
	int insert(CountryLeagueSeasonMasterEntity entity);

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
