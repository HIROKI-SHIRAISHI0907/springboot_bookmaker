package dev.application.domain.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.CountryLeagueSeasonMasterEntity;

/**
 * country_league_season_master Mapper。
 *
 * 【修正履歴】
 * ・insert の VALUES で #{delFlg} の後ろのカンマが抜けており、SQL 構文エラーになっていたのを修正
 * ・seq 採番用に findSeasonYearInPeriod / findLatestStartedSeasonYear / findLatestSeasonYear を追加
 *   （CountryLeagueSeasonResolver で使用。valid_flg では絞らない）
 *
 * 【懸念点】
 * ・【重要】findCurrentSeasonYear は valid_flg = '0' で絞っているが、実データでは主要リーグ
 *   （J1・ラ・リーガ等、日付が登録済みのリーグ）が valid_flg = '1' になっている。
 *   '1' が「有効」の意味なら、このメソッドは主要リーグでシーズンを返さない。呼び出し元を確認すること。
 * ・findByCountryAndLeague は season_year / valid_flg / del_flg を SELECT していないため、
 *   Entity のそれらの項目は null になる。del_flg='1'（削除済み）の行も返る。
 * ・update / updateFlg は id を INTEGER にキャストしている。id 列が bigint の場合、約21億を超えると失敗する。
 * ・updateFlg で country / league も更新している（フラグ更新のついでに値が変わる可能性がある）。
 */
@Mapper
public interface CountryLeagueSeasonMasterRepository {

	@Insert({
			"INSERT INTO country_league_season_master (",
			"country, league, season_year, start_season_date, end_season_date, round, path, icon, valid_flg, del_flg,",
			"register_id, register_time, update_id, update_time) VALUES (",
			"#{country}, #{league}, #{seasonYear}, CAST(#{startSeasonDate} AS timestamptz), "
					+ "CAST(#{endSeasonDate} AS timestamptz), #{round}, #{path}, #{icon}, #{validFlg}, #{delFlg},",
			"#{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz));"
	})
	int insert(CountryLeagueSeasonMasterEntity entity);

	@Update({
			"UPDATE country_league_season_master SET",
			"country = #{country},",
			"league = #{league},",
			"season_year = #{seasonYear},",
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
	List<CountryLeagueSeasonMasterEntity> findByCountryAndLeague(@Param("country") String country,
			@Param("league") String league);

	@Select({
			"SELECT country, league, round FROM country_league_season_master ",
			"WHERE valid_flg = #{validFlg}"
	})
	List<CountryLeagueSeasonMasterEntity> findRoundValidFlg(@Param("validFlg") String validFlg);

	/**
	 * 【追加・BM_M031 用】全シーズンの総ラウンド数（削除済みを除く）。
	 * 序盤/中盤/終盤の判定に使う。valid_flg（収集対象かどうか）では絞らない
	 * （valid_flg = '0' で絞ると、主要リーグが対象外になるため）。
	 */
	@Select({
			"SELECT country, league, season_year, round FROM country_league_season_master ",
			"WHERE del_flg = '0'"
	})
	List<CountryLeagueSeasonMasterEntity> findAllRounds();

	/**
	 * 今日がシーズン期間内の有効なシーズンを1件返す（なければ null）。
	 */
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
	String findCurrentSeasonYear(@Param("country") String country,
			@Param("league") String league);

	/**
	 * 【追加・seq採番用】今日がシーズン期間内のシーズンを1件返す（なければ null）。
	 * valid_flg（収集対象かどうか）では絞らない。del_flg='0' のみ。
	 */
	@Select({
			"SELECT season_year ",
			"FROM country_league_season_master ",
			"WHERE country = #{country} ",
			"  AND league  = #{league} ",
			"  AND del_flg = '0' ",
			"  AND NOW() BETWEEN start_season_date AND end_season_date ",
			"ORDER BY start_season_date DESC ",
			"LIMIT 1"
	})
	String findSeasonYearInPeriod(@Param("country") String country,
			@Param("league") String league);

	/**
	 * 【追加・seq採番用】今日までに開始した直近のシーズンを1件返す（なければ null）。
	 * シーズン終了後〜次シーズン開始前（オフ期間）でも、直前のシーズンが取れる。
	 */
	@Select({
			"SELECT season_year ",
			"FROM country_league_season_master ",
			"WHERE country = #{country} ",
			"  AND league  = #{league} ",
			"  AND del_flg = '0' ",
			"  AND start_season_date <= NOW() ",
			"ORDER BY start_season_date DESC ",
			"LIMIT 1"
	})
	String findLatestStartedSeasonYear(@Param("country") String country,
			@Param("league") String league);

	/**
	 * 【追加・seq採番用】開始日・終了日が未登録の行も含め、season_year が最も新しいものを1件返す（なければ null）。
	 * 例: ケニア プレミアリーグ（2025/2026、日付なし）のような行の救済用。
	 * 並びは文字列順（"2026/2027" &gt; "2026" &gt; "2025/2026"）。
	 */
	@Select({
			"SELECT season_year ",
			"FROM country_league_season_master ",
			"WHERE country = #{country} ",
			"  AND league  = #{league} ",
			"  AND del_flg = '0' ",
			"  AND season_year IS NOT NULL ",
			"ORDER BY season_year DESC ",
			"LIMIT 1"
	})
	String findLatestSeasonYear(@Param("country") String country,
			@Param("league") String league);

	/**
	 * 1件以上引っかかった場合を想定
	 * @param country
	 * @param league
	 * @return
	 */
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
	List<String> findSeasonYear(@Param("country") String country,
			@Param("league") String league);

}