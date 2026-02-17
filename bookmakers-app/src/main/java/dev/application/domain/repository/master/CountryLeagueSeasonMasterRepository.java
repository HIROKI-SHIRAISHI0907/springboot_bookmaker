package dev.application.domain.repository.master;

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
			"country, league, start_season_date, end_season_date, round, path, icon, valid_flg, del_flg,",
			"register_id, register_time, update_id, update_time) VALUES (",
			"#{country}, #{league}, CAST(#{startSeasonDate} AS timestamptz), "
					+ "CAST(#{endSeasonDate} AS timestamptz), #{round}, #{path}, #{icon}, #{validFlg}, #{delFlg}",
			"#{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz));"
	})
	int insert(CountryLeagueSeasonMasterEntity entity);

	@Update({
			"UPDATE country_league_season_master SET",
			"country = #{country},",
			"league = #{league},",
			"start_season_date = CAST(#{startSeasonDate} AS timestamptz),",
			"end_season_date = CAST(#{endSeasonDate} AS timestamptz) ",
			"WHERE id = CAST(#{id,jdbcType=VARCHAR} AS INTEGER);"
	})
	int update(CountryLeagueSeasonMasterEntity entity);

	@Update({
			"UPDATE country_league_season_master SET",
			"country = #{country},",
			"league = #{league},",
			"valid_flg = #{validFlg}",
			" WHERE id = CAST(#{id,jdbcType=VARCHAR} AS INTEGER);"
	})
	int updateFlg(CountryLeagueSeasonMasterEntity entity);

	@Select({
			"SELECT id, country, league, start_season_date, end_season_date, round, path "
					+ "FROM country_league_season_master ",
			"WHERE country = #{country} AND league = #{league}"
	})
	List<CountryLeagueSeasonMasterEntity> findByCountryAndLeague(String country, String league);

	@Select({
			"SELECT country, league, round FROM country_league_season_master ",
			"WHERE disp_valid_flg = #{validFlg}"
	})
	List<CountryLeagueSeasonMasterEntity> findRoundValidFlg(String validFlg);

	@Select({
	    "SELECT season_year ",
	    "FROM country_league_season_master ",
	    "WHERE country = #{country} ",
	    "  AND league  = #{league} ",
	    "  AND valid_flg = '0' ",
	    "  AND del_flg = '0' ",
	    "  AND NOW() BETWEEN start_season_date AND end_season_date ",
	    "ORDER BY start_season_date DESC ",
	    "LIMIT 1"
	})
	String findCurrentSeasonYear(String country, String league);

}
