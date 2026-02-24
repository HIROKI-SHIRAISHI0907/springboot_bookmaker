package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.CountryLeagueMasterEntity;

@Mapper
public interface CountryLeagueMasterBatchRepository {

	@Insert({
			"INSERT INTO country_league_master (",
			"    country,",
			"    league,",
			"    team,",
			"    link,",
			"    del_flg,",
			"    register_id,",
			"    register_time,",
			"    update_id,",
			"    update_time",
			") VALUES (",
			"    #{country},",
			"    #{league},",
			"    #{team},",
			"    #{link},",
			"    '0',",
			"    #{registerId},",
			"	 CURRENT_TIMESTAMP",
			"	 {updateId},",
			"	 CURRENT_TIMESTAMP)",
	})
	int insert(CountryLeagueMasterEntity entity);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        team,
			        link,
			        del_flg
			    FROM
			    	country_league_master
			    WHERE
			        country = #{country} AND
			        league = #{league} AND
			        team = #{team} AND
			        del_flg = '0';
			""")
	CountryLeagueMasterEntity findByCountryLeague(
			@Param("country") String country,
			@Param("league") String league,
			@Param("team") String team);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        team,
			        link,
			        del_flg
			    FROM
			    	country_league_master
			    ORDER BY
			    	id;
			""")
	List<CountryLeagueMasterEntity> findData();

	/**
	 * 指定した国・リーグの「未削除（del_flg=0）」一覧を取得
	 */
	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        team,
			        link,
			        del_flg
			    FROM
			    	country_league_master
			    WHERE
			    	country = #{country} AND
			    	league  = #{league} AND
			    	del_flg = '0';
			""")
	List<CountryLeagueMasterEntity> findActiveByCountryAndLeague(
			@Param("country") String country,
			@Param("league") String league);

	/**
	 * 指定した国・リーグの「未削除（del_flg=0）」一覧を取得
	 */
	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        team,
			        link,
			        del_flg
			    FROM
			    	country_league_master
			    WHERE
			    	country = #{country} AND
			    	del_flg = '0';
			""")
	List<CountryLeagueMasterEntity> findActiveByCountry(
			@Param("country") String country);

	@Update("""
			    UPDATE country_league_master
			    SET
			        league = #{league},
					team   = #{team},
					link   = #{link},
					update_time = CURRENT_TIMESTAMP
			    WHERE id = #{id};
			""")
	int updateById(@Param("league") String league,
			@Param("team") String team,
			@Param("link") String link,
			@Param("id") Integer id);

	@Update("""
			    UPDATE country_league_master
			    SET
					del_flg = '1',
					update_time = CURRENT_TIMESTAMP
			    WHERE id = #{id} AND del_flg = '0';
			""")
	int logicalDeleteById(@Param("id") Integer id);

	@Update("""
			    UPDATE country_league_master
			    SET
					del_flg = '0',
					update_time = CURRENT_TIMESTAMP
			    WHERE id = #{id} AND del_flg = '1';
			""")
	int reviveById(@Param("id") Integer id);

}