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
			"    sub_league,",
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
			"    #{subLeague},",
			"    #{team},",
			"    #{link},",
			"    '0',",
			"    'SYSTEM',",
			"    CURRENT_TIMESTAMP,",
			"    'SYSTEM',",
			"    CURRENT_TIMESTAMP)",
	})
	int insert(CountryLeagueMasterEntity entity);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        sub_league AS subLeague,
			        team,
			        link,
			        del_flg AS delFlg
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
			        sub_league AS subLeague,
			        team,
			        link,
			        del_flg AS delFlg
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
			        sub_league AS subLeague,
			        team,
			        link,
			        del_flg AS delFlg
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
	 * 指定した国の「未削除（del_flg=0）」一覧を取得
	 */
	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        sub_league AS subLeague,
			        team,
			        link,
			        del_flg AS delFlg
			    FROM
			    	country_league_master
			    WHERE
			    	country = #{country} AND
			    	del_flg = '0';
			""")
	List<CountryLeagueMasterEntity> findActiveByCountry(
			@Param("country") String country);

	/**
	 * チーム名から未削除の国・リーグを1件取得
	 * フォールバック用
	 */
	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        sub_league AS subLeague,
			        team,
			        link,
			        del_flg AS delFlg
			    FROM
			    	country_league_master
			    WHERE
			    	team = #{team}
			    	AND del_flg = '0'
			    ORDER BY
			    	id DESC
			    LIMIT 1
			""")
	CountryLeagueMasterEntity findActiveByTeam(@Param("team") String team);

	/**
	 * 国リーグから未削除の国・リーグを1件取得
	 * フォールバック用
	 */
	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        sub_league AS subLeague,
			        team,
			        link,
			        del_flg AS delFlg
			    FROM
			    	country_league_master
			    WHERE
			    	country = #{country} AND
			    	league  = #{league} AND
			    	del_flg = '1'
			    ORDER BY
			    	id DESC
			""")
	List<CountryLeagueMasterEntity> findDelete(@Param("country") String country,
			@Param("league") String league);

	/**
	 * home/away の両チームが所属する共通の国・リーグを1件取得
	 * 例:
	 *   home=鹿島アントラーズ, away=浦和レッズ
	 *   -> 日本 / J1
	 */
	@Select("""
			    SELECT
			    	MAX(id) AS id,
			    	country,
			    	league,
			    	MAX(sub_league) AS subLeague,
			    	NULL AS team,
			    	MAX(link) AS link,
			    	'0' AS delFlg
			    FROM
			    	country_league_master
			    WHERE
			    	team IN (#{homeTeamName}, #{awayTeamName})
			    	AND del_flg = '0'
			    GROUP BY
			    	country,
			    	league
			    HAVING
			    	COUNT(DISTINCT team) = 2
			    ORDER BY
			    	MAX(id) DESC
			    LIMIT 1
			""")
	CountryLeagueMasterEntity findCommonCountryLeagueByTeams(
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

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

	@Update("""
		    UPDATE country_league_master
		    SET
				del_flg = '1',
				update_time = CURRENT_TIMESTAMP
		    WHERE country = #{country} AND league = #{league} AND del_flg = '0';
		""")
	int logicalDeleteByCountryLeague(@Param("country") String country,
			@Param("league") String league);

}
