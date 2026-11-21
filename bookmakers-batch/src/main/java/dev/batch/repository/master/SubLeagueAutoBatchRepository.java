package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * サブリーグ保有国リーグ登録設定の自動反映（B008。2026-11-21 追加）
 *
 * <ul>
 *   <li>country_league_sub_league_master: 国・リーグ ごとの sub_league_flg（'1' あり / '0' なし）</li>
 *   <li>country_league_sub_league_name_master: サブリーグ名（disp_order 順）</li>
 * </ul>
 * 画面（web の SubLeagueSeasonRepository）と同じテーブル・同じ書き方。
 */
@Mapper
public interface SubLeagueAutoBatchRepository {

	/** 国 の対象リーグ（country_league_season_master の del_flg = '0'） */
	@Select("""
			    SELECT DISTINCT league
			    FROM country_league_season_master
			    WHERE country = #{country}
			      AND del_flg = '0'
			""")
	List<String> findSeasonLeagues(@Param("country") String country);

	/** 設定（無ければ null） */
	@Select("""
			    SELECT sub_league_flg
			    FROM country_league_sub_league_master
			    WHERE country = #{country} AND league = #{league}
			""")
	String findFlg(@Param("country") String country, @Param("league") String league);

	/** サブリーグ名（表示順） */
	@Select("""
			    SELECT sub_league
			    FROM country_league_sub_league_name_master
			    WHERE country = #{country} AND league = #{league}
			    ORDER BY disp_order, id
			""")
	List<String> findNames(@Param("country") String country, @Param("league") String league);

	/** 設定を「あり」で登録・更新 */
	@Insert("""
			    INSERT INTO country_league_sub_league_master (
			        country, league, sub_league_flg, register_id, register_time, update_id, update_time
			    ) VALUES (
			        #{country}, #{league}, '1', #{user}, CURRENT_TIMESTAMP, #{user}, CURRENT_TIMESTAMP
			    )
			    ON CONFLICT (country, league) DO UPDATE SET
			        sub_league_flg = '1',
			        update_id      = EXCLUDED.update_id,
			        update_time    = CURRENT_TIMESTAMP
			""")
	int upsertHas(@Param("country") String country, @Param("league") String league, @Param("user") String user);

	@Delete("""
			    DELETE FROM country_league_sub_league_name_master
			    WHERE country = #{country} AND league = #{league}
			""")
	int deleteNames(@Param("country") String country, @Param("league") String league);

	@Insert("""
			    INSERT INTO country_league_sub_league_name_master (
			        country, league, sub_league, disp_order, register_id, register_time, update_id, update_time
			    ) VALUES (
			        #{country}, #{league}, #{subLeague}, #{dispOrder}, #{user}, CURRENT_TIMESTAMP, #{user}, CURRENT_TIMESTAMP
			    )
			""")
	int insertName(@Param("country") String country, @Param("league") String league,
			@Param("subLeague") String subLeague, @Param("dispOrder") int dispOrder, @Param("user") String user);
}
