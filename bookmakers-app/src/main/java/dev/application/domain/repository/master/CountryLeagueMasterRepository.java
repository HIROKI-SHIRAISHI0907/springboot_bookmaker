package dev.application.domain.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.common.entity.CountryLeagueMasterEntity;

/**
 * country_league_master の Repository（BM_M001 ほか）
 *
 * <p>2026-10 追加: findCommonCountryLeagueByTeams（両チームが同じ国・リーグに属するもの。同名チーム対策）</p>
 */
@Mapper
public interface CountryLeagueMasterRepository {

	@Insert("""
			    INSERT INTO country_league_master (
			        country,
			        league,
			        team,
			        link,
			        register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			        #{country},
			        #{league},
			        #{team},
			        #{link},
			        #{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz)
			    );
			""")
	int insert(CountryLeagueMasterEntity entity);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        team,
			        link
			    FROM
			    	country_league_master
			    WHERE
			        country = #{country} AND
			        league = #{league} AND
			        team = #{team};
			""")
	List<CountryLeagueMasterEntity> findByCountryLeague(String country, String league, String team);

	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        team
			    FROM
			    	country_league_master;
			""")
	List<CountryLeagueMasterEntity> findData();

	/**
	 * チームから国リーグを1件取得
	 */
	@Select("""
			    SELECT
			    	id,
			        country,
			        league,
			        sub_league AS subLeague,
			        team
			    FROM
			    	country_league_master
			    WHERE
			    	normalize(team, NFKC) = normalize(#{team}, NFKC)
			    AND
			    	del_flg = '0'
			    LIMIT 1;
			""")
	CountryLeagueMasterEntity findCountryLeagueByTeam(@Param("team") String team);

	/**
	 * home/away の両チームが所属する共通の国・リーグを1件取得（NFKC 一致）。無ければ null。
	 */
	@Select("""
			    SELECT
			    	MAX(id) AS id,
			    	country,
			    	league,
			    	MAX(sub_league) AS subLeague,
			    	NULL AS team
			    FROM
			    	country_league_master
			    WHERE
			    	normalize(team, NFKC) IN (normalize(#{homeTeamName}, NFKC), normalize(#{awayTeamName}, NFKC))
			    	AND del_flg = '0'
			    GROUP BY
			    	country,
			    	league
			    HAVING
			    	COUNT(DISTINCT normalize(team, NFKC)) = 2
			    ORDER BY
			    	MAX(id) DESC
			    LIMIT 1
			""")
	CountryLeagueMasterEntity findCommonCountryLeagueByTeams(
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

}
