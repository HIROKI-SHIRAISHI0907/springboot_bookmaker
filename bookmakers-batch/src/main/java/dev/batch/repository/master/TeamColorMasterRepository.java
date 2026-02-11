package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.batch.bm_b004.TeamColorMasterEntity;

@Mapper
public interface TeamColorMasterRepository {

	@Insert("""
			    INSERT INTO team_color_master (
			        country,
			        league,
			        team,
			        team_color_main_hex,
			        team_color_sub_hex,
			        register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			        #{country},
			        #{league},
			        #{team},
			        #{teamColorMainHex},
			        #{teamColorSubHex},
			        "SYSTEM",
			        CURRENT_TIMESTAMP,
			        "SYSTEM",
			        CURRENT_TIMESTAMP
			    );
			""")
	int insert(TeamColorMasterEntity entity);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        team,
			        team_color_hex
			    FROM
			    	team_color_master
			    WHERE
			        country = #{country} AND
			        league = #{league} AND
			        team = #{team};
			""")
	TeamColorMasterEntity findByCountryLeague(
			@Param("country") String country,
			@Param("league") String league,
			@Param("team") String team);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        team
			    FROM
			    	team_color_master;
			""")
	List<TeamColorMasterEntity> findData();

}