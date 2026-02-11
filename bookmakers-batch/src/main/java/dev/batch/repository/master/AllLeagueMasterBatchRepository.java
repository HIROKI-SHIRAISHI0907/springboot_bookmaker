package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.AllLeagueMasterEntity;
import dev.common.entity.CountryLeagueMasterEntity;

@Mapper
public interface AllLeagueMasterBatchRepository {

	@Insert({
			    "INSERT INTO all_league_scrape_master (",
			    "    country,",
			    "    league,",
			    "    team,",
			    "    logic_flg,",
			    "    register_id,",
			    "    register_time,",
			    "    update_id,",
			    "    update_time",
			    ") VALUES (",
			    "    #{country},",
			    "    #{league},",
			    "    #{logicFlg},",
			    "    #{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz))",
			    //"#{registerId}, #{registerTime}, #{updateId}, #{updateTime});"
	})
	int insert(AllLeagueMasterEntity entity);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        logic_flg
			    FROM
			    	all_league_scrape_master
			    WHERE
			        country = #{country} AND
			        league = #{league};
			""")
	AllLeagueMasterEntity findByCountryLeague(
			@Param("country") String country,
			@Param("league") String league);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        logic_flg
			    FROM
			    	all_league_scrape_master
			    ORDER BY
			    	id;
			""")
	List<AllLeagueMasterEntity> findData();

	/**
	 * 指定した国・リーグの「論理削除（logic_flg=0）」一覧を取得
	 */
	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        logic_flg
			    FROM
			    	all_league_scrape_master
			    WHERE
			    	country = #{country} AND
			    	league  = #{league} AND
			    	logic_flg = '0';
			""")
	List<CountryLeagueMasterEntity> findActiveByCountryAndLeague(
			@Param("country") String country,
			@Param("league") String league);

	@Update("""
			    UPDATE all_league_scrape_master
			    SET
			        league = #{league},
					team   = #{team},
					update_time = CURRENT_TIMESTAMP
			    WHERE id = #{id};
			""")
	int updateById(@Param("league") String league,
			@Param("team") String team,
			@Param("id") Integer id);

	@Update("""
			    UPDATE all_league_scrape_master
			    SET
					logic_flg = '1',
					update_time = CURRENT_TIMESTAMP
			    WHERE id = #{id} AND logic_flg = '0';
			""")
	int logicalDeleteById(@Param("id") Integer id);

	@Update("""
			    UPDATE all_league_scrape_master
			    SET
					logic_flg = '0',
					update_time = CURRENT_TIMESTAMP
			    WHERE id = #{id};
			""")
	int reviveById(@Param("id") Integer id);

}