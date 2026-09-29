package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m006.CountryLeagueSummaryEntity;

/**
 * country_league_summary Mapper（BM_M006 国・リーグ別 処理件数）。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>{@link #upsertAdd}: 行がなければ INSERT、あれば csv_count に加算する（1本の SQL で行うため、
 *       同時実行でも加算が消えない）。</li>
 *   <li>{@link #findSeq}: 既存行の seq を取得する（新規行のときだけ採番するため）。</li>
 *   <li>{@link #findBySeasonCountryLeague} / {@link #findByCountryLeague}: 参照用。</li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT）。</li>
 *   <li><b>同時実行で同じキーの新規行を2つの処理が採番した場合</b>: 後の INSERT は加算になり、後の番号は欠番になる
 *       （行の重複・加算漏れは起きない）。</li>
 *   <li><b>メタデータは NOT NULL</b>。Entity に値がなければ ID='SYSTEM'、日時=NOW()。加算時は register_* を変えない。</li>
 * </ul>
 */
@Mapper
public interface CountryLeagueSummaryRepository {

	/**
	 * シーズン・国・リーグで1行取得（なければ null）。
	 */
	@Results(id = "countryLeagueSummary", value = {
			@Result(column = "seq", property = "seq"),
			@Result(column = "season", property = "season"),
			@Result(column = "country", property = "country"),
			@Result(column = "league", property = "league"),
			@Result(column = "csv_count", property = "csvCount"),
			@Result(column = "register_id", property = "registerId"),
			@Result(column = "register_time", property = "registerTime"),
			@Result(column = "update_id", property = "updateId"),
			@Result(column = "update_time", property = "updateTime")
	})
	@Select({
			"SELECT seq, season, country, league, csv_count, ",
			"register_id, register_time, update_id, update_time ",
			"FROM country_league_summary ",
			"WHERE season = #{season} AND country = #{country} AND league = #{league}"
	})
	CountryLeagueSummaryEntity findBySeasonCountryLeague(
			@Param("season") String season,
			@Param("country") String country,
			@Param("league") String league);

	/**
	 * 国・リーグの全シーズン分（新しいシーズン順）。
	 */
	@Select({
			"SELECT seq, season, country, league, csv_count, ",
			"register_id, register_time, update_id, update_time ",
			"FROM country_league_summary ",
			"WHERE country = #{country} AND league = #{league} ",
			"ORDER BY season DESC"
	})
	@ResultMap("countryLeagueSummary")
	List<CountryLeagueSummaryEntity> findByCountryLeague(
			@Param("country") String country,
			@Param("league") String league);

	/**
	 * 既存行の seq（なければ null）。
	 */
	@Select({
			"SELECT seq FROM country_league_summary ",
			"WHERE season = #{season} AND country = #{country} AND league = #{league}"
	})
	String findSeq(
			@Param("season") String season,
			@Param("country") String country,
			@Param("league") String league);

	/**
	 * 行がなければ INSERT、あれば csv_count に加算する。
	 *
	 * @param entity seq・season・country・league と、今回の加算分を csvCount に入れる
	 * @return 処理件数（INSERT・加算とも 1）
	 */
	@Insert({
			"INSERT INTO country_league_summary (",
			"seq, season, country, league, csv_count, ",
			"register_id, register_time, update_id, update_time",
			") VALUES (",
			"#{seq}, #{season}, #{country}, #{league}, #{csvCount}, ",
			"COALESCE(#{registerId}, 'SYSTEM'), COALESCE(CAST(#{registerTime} AS timestamptz), NOW()), ",
			"COALESCE(#{updateId}, 'SYSTEM'), COALESCE(CAST(#{updateTime} AS timestamptz), NOW())",
			") ON CONFLICT (season, country, league) DO UPDATE SET ",
			// seq・一意キー・register_* は変えない
			"csv_count = country_league_summary.csv_count + EXCLUDED.csv_count, ",
			"update_id = EXCLUDED.update_id, update_time = EXCLUDED.update_time"
	})
	int upsertAdd(CountryLeagueSummaryEntity entity);
}
