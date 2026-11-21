package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 試合予定がシーズン期間（country_league_season_master）の中かを調べる（B005。2026-11-21 追加）
 */
@Mapper
public interface SeasonRangeBatchRepository {

	/** 国 の対象リーグ（del_flg = '0'） */
	@Select("""
			    SELECT DISTINCT league
			    FROM country_league_season_master
			    WHERE country = #{country}
			      AND del_flg = '0'
			""")
	List<String> findLeagues(@Param("country") String country);

	/**
	 * 国・リーグ のシーズンの行ごとに、試合開始日時（日本時間の "yyyy-MM-dd HH:mm:ss"）が期間内か。
	 * @return 行ごとに UNKNOWN（開始日か終了日が無い）/ IN（開始日〜終了日）/ OUT（期間の外）
	 */
	@Select("""
			    SELECT CASE
			             WHEN start_season_date IS NULL OR end_season_date IS NULL THEN 'UNKNOWN'
			             WHEN (CAST(#{futureTime} AS timestamp) AT TIME ZONE 'Asia/Tokyo') >= start_season_date
			              AND (CAST(#{futureTime} AS timestamp) AT TIME ZONE 'Asia/Tokyo') <= end_season_date THEN 'IN'
			             ELSE 'OUT'
			           END
			    FROM country_league_season_master
			    WHERE country = #{country}
			      AND league = #{league}
			      AND del_flg = '0'
			""")
	List<String> checkRange(@Param("country") String country, @Param("league") String league,
			@Param("futureTime") String futureTime);
}
