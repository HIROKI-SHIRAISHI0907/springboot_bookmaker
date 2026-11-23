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

	/**
	 * シーズン終了フラグを立てる（B013。2026-11-21 変更）。
	 * 以前は end_season_date を NULL にしていたが、終了日が無いとシーズン期間で絞る処理（試合予定の表示・登録）で
	 * 過去の試合まで期間外になるため、終了日は残してフラグだけ立てる。
	 * 新しいシーズンの終了日が入ると（end_season_date が変わると）各 UPDATE で '0' に戻る。
	 */
	@Update("""
			    UPDATE country_league_season_master
			    SET
			        season_end_flg = '1',
			        del_flg = '0',
			        update_time = CURRENT_TIMESTAMP,
			        update_id = 'BATCH'
			    WHERE
			        country = #{country}
			        AND league = #{league}
			""")
	int markSeasonEnd(@Param("country") String country,
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
					AND COALESCE(season_end_flg, '0') = '0'
			""")
	List<CountryLeagueSeasonMasterEntity> findDateList();

	/**
	 * b025_fin_season_all_data.json 同期用。有効なシーズンの 国・リーグ・終了日時（日本時間）を返す。
	 * endSeasonDate は "yyyy-MM-dd HH:mm:ss+09"（web の出力と同じ形式）。終了日が無い行は NULL。
	 * 同じ国リーグが複数行ある場合に新しい終了日時が後に来るよう、NULL を先に並べる。
	 */
	@Select("""
			    SELECT
			        country,
			        league,
			        to_char(end_season_date AT TIME ZONE 'Asia/Tokyo', 'YYYY-MM-DD HH24:MI:SS') || '+09' AS endSeasonDate
			    FROM
			        country_league_season_master
			    WHERE
			        del_flg = '0'
			    ORDER BY country, league, end_season_date NULLS FIRST
			""")
	List<CountryLeagueSeasonMasterEntity> findEndSeasonDateJstList();

	/**
	 * サブリーグ保有国リーグ登録設定（country_league_sub_league_master）で「サブリーグあり」の 国・リーグ（2026-11 追加）
	 * @return "国-リーグ"（b025_fin_season_data.json のキーと同じ形）
	 */
	@Select("""
			    SELECT
			        country || '-' || league
			    FROM
			        country_league_sub_league_master
			    WHERE
			        sub_league_flg = '1'
			""")
	List<String> findSubLeagueCountryLeagueKeys();

	/**
	 * スクレイピング対象外の 国・リーグ（all_league_scrape_master の同じ国・リーグの行がすべて disp_flg = '1'。2026-11-24 追加）
	 * 次シーズン取得の判定（Lambda next-season-data-check-json-outputs）で取りに行かないようにするため。
	 * @return "国-リーグ"（b025_fin_season_all_data.json のキーと同じ形）
	 */
	@Select("""
			    SELECT BTRIM(country) || '-' || BTRIM(league)
			    FROM all_league_scrape_master
			    WHERE NULLIF(BTRIM(country), '') IS NOT NULL
			      AND NULLIF(BTRIM(league), '') IS NOT NULL
			    GROUP BY BTRIM(country), BTRIM(league)
			    HAVING NOT BOOL_OR(COALESCE(disp_flg, '0') = '0')
			""")
	List<String> findScrapeExcludedCountryLeagueKeys();

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
			"			season_end_flg = CASE WHEN #{endSeasonDate}::timestamptz IS DISTINCT FROM end_season_date THEN '0' ELSE season_end_flg END,",
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

	/**
	 * 国・リーグの行を1件取得（B003 の登録・更新判定用）。
	 * 非表示（del_flg = '1'）の行も対象にする（現役を優先、同じなら新しい id）。
	 * 以前は del_flg = '0' だけを見ていたため、非表示のリーグの新シーズンを取得すると
	 * 同じ国・リーグの行が新しく INSERT され、行が増えていた（2026-10-30 修正）。
	 */
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
			    ORDER BY
			      CASE WHEN del_flg = '0' THEN 0 ELSE 1 END,
			      id DESC
			    LIMIT 1
			""")
	CountryLeagueSeasonMasterEntity findLatestByCountryLeague(
			@Param("country") String country,
			@Param("league") String league);

	/**
	 * 管理画面で開始日・終了日を手修正した行か（manual_date_flg = '1'。2026-11-24 追加）
	 */
	@Select("""
			    SELECT COUNT(*) FROM country_league_season_master
			    WHERE id = #{id} AND manual_date_flg = '1'
			""")
	int countManualDateLocked(@Param("id") Integer id);

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

			       -- 2026-11-24 シーズン年が変わったら手修正の目印を外す
			       manual_date_flg = CASE WHEN season_year IS DISTINCT FROM #{seasonYear} THEN '0' ELSE manual_date_flg END,

			       start_season_date = CASE
			           WHEN #{startSeasonDate} IS NULL THEN start_season_date
			           WHEN BTRIM(#{startSeasonDate}) = '' THEN start_season_date
			           WHEN LOWER(BTRIM(#{startSeasonDate})) = 'n/a' THEN start_season_date
			           WHEN LOWER(BTRIM(#{startSeasonDate})) = 'null' THEN start_season_date
			           WHEN BTRIM(#{startSeasonDate}) = '-' THEN start_season_date
			           WHEN BTRIM(#{startSeasonDate}) = '未定' THEN start_season_date
			           ELSE CAST(#{startSeasonDate} AS timestamptz)
			       END,

			       season_end_flg = CASE
			           WHEN #{endSeasonDate} IS NULL THEN season_end_flg
			           WHEN BTRIM(#{endSeasonDate}) IN ('', '-', '未定') THEN season_end_flg
			           WHEN LOWER(BTRIM(#{endSeasonDate})) IN ('n/a', 'null') THEN season_end_flg
			           WHEN CAST(#{endSeasonDate} AS timestamptz) IS DISTINCT FROM end_season_date THEN '0'
			           ELSE season_end_flg
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
