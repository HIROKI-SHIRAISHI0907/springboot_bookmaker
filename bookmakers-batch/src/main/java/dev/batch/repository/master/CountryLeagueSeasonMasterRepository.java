package dev.batch.repository.master;

import java.util.List;

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
			        end_season_date = '---',
			        update_time = CURRENT_TIMESTAMP,
			        update_id = 'BATCH'
			    WHERE
			        country = #{country}
			        AND league = #{league}
			""")
	int clearEndSeasonDate(
			@Param("country") String country,
			@Param("league") String league);

}
