package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.batch.bm_b015.FutureUploadParam;
import dev.batch.bm_b015.SeasonUploadParam;
import dev.batch.bm_b015.TeamUploadParam;

/**
 * CSV アップロードの反映（B015）用リポジトリ（master DB）
 *
 * <ul>
 *   <li>country_league_season_master（シーズン）/ country_league_master（チーム）/ future_master（試合予定）/
 *       point_setting_master（シーズンの非表示・再表示に合わせる）</li>
 *   <li>キーの比較は全角・半角の揺れと前後の空白を無視する（normalize(…, NFKC)）。del_flg は問わない（非表示の行も対象）。</li>
 *   <li>更新は「値が入っている項目だけ」SET する（null の項目は変更しない）。</li>
 *   <li>日時は「yyyy-MM-ddTHH:mm:ss+09:00」で受け取り、timestamptz にして保存する。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Mapper
public interface CsvUploadMasterBatchRepository {

	// =========================================================
	// シーズン（country_league_season_master）
	// =========================================================

	/** 国 + リーグで ID を探す */
	@Select("""
			SELECT id
			  FROM country_league_season_master
			 WHERE normalize(BTRIM(country), NFKC) = normalize(BTRIM(#{country}), NFKC)
			   AND normalize(BTRIM(league), NFKC) = normalize(BTRIM(#{league}), NFKC)
			 ORDER BY id
			""")
	List<Long> findSeasonIds(@Param("country") String country, @Param("league") String league);

	/** ID の存在確認 */
	@Select("SELECT COUNT(*) FROM country_league_season_master WHERE id = CAST(#{id} AS BIGINT)")
	int countSeasonById(@Param("id") long id);

	/** ID から国・リーグを取る（point_setting_master を合わせるため） */
	@Select("""
			SELECT id, country, league
			  FROM country_league_season_master
			 WHERE id = CAST(#{id} AS BIGINT)
			""")
	SeasonUploadParam findSeasonById(@Param("id") long id);

	/** 登録（表示状態） */
	@Insert("""
			INSERT INTO country_league_season_master (
			    country, league, season_year, start_season_date, end_season_date,
			    round, path, icon, valid_flg, del_flg,
			    register_id, register_time, update_id, update_time
			) VALUES (
			    #{country}, #{league}, #{seasonYear,jdbcType=VARCHAR},
			    CAST(#{startSeasonDate,jdbcType=VARCHAR} AS timestamptz),
			    CAST(#{endSeasonDate,jdbcType=VARCHAR} AS timestamptz),
			    #{round,jdbcType=VARCHAR}, #{path,jdbcType=VARCHAR}, #{icon,jdbcType=VARCHAR},
			    #{validFlg}, '0',
			    #{user}, CURRENT_TIMESTAMP, #{user}, CURRENT_TIMESTAMP
			)
			""")
	int insertSeason(SeasonUploadParam p);

	/** 更新（値が入っている項目だけ） */
	@Update("""
			<script>
			UPDATE country_league_season_master
			<set>
			  <if test="country != null">country = #{country},</if>
			  <if test="league != null">league = #{league},</if>
			  <if test="seasonYear != null">season_year = #{seasonYear},</if>
			  <if test="startSeasonDate != null">start_season_date = CAST(#{startSeasonDate} AS timestamptz),</if>
			  <if test="endSeasonDate != null">end_season_date = CAST(#{endSeasonDate} AS timestamptz),</if>
			  <if test="round != null">round = #{round},</if>
			  <if test="path != null">path = #{path},</if>
			  <if test="icon != null">icon = #{icon},</if>
			  <if test="validFlg != null">valid_flg = #{validFlg},</if>
			  update_id = #{user},
			  update_time = CURRENT_TIMESTAMP
			</set>
			WHERE id = CAST(#{id} AS BIGINT)
			</script>
			""")
	int updateSeason(SeasonUploadParam p);

	/** 物理削除 */
	@Delete("DELETE FROM country_league_season_master WHERE id = CAST(#{id} AS BIGINT)")
	int deleteSeason(@Param("id") long id);

	/** 非表示（'1'）/ 再表示（'0'） */
	@Update("""
			UPDATE country_league_season_master
			   SET del_flg = #{delFlg}, update_id = #{user}, update_time = CURRENT_TIMESTAMP
			 WHERE id = CAST(#{id} AS BIGINT)
			""")
	int updateSeasonDelFlg(@Param("id") long id, @Param("delFlg") String delFlg, @Param("user") String user);

	/** point_setting_master の del_flg を国・リーグで合わせる（シーズンの非表示・再表示と同じ値） */
	@Update("""
			UPDATE point_setting_master
			   SET del_flg = #{delFlg}, update_id = #{user}, update_time = CURRENT_TIMESTAMP
			 WHERE country = #{country}
			   AND league = #{league}
			""")
	int updatePointSettingDelFlg(@Param("country") String country, @Param("league") String league,
			@Param("delFlg") String delFlg, @Param("user") String user);

	// =========================================================
	// チーム（country_league_master）
	// =========================================================

	/** 国 + リーグ + チームで ID を探す */
	@Select("""
			SELECT id
			  FROM country_league_master
			 WHERE normalize(BTRIM(country), NFKC) = normalize(BTRIM(#{country}), NFKC)
			   AND normalize(BTRIM(league), NFKC) = normalize(BTRIM(#{league}), NFKC)
			   AND normalize(BTRIM(team), NFKC) = normalize(BTRIM(#{team}), NFKC)
			 ORDER BY id
			""")
	List<Long> findTeamIds(@Param("country") String country, @Param("league") String league, @Param("team") String team);

	/** ID の存在確認 */
	@Select("SELECT COUNT(*) FROM country_league_master WHERE id = CAST(#{id} AS BIGINT)")
	int countTeamById(@Param("id") long id);

	/** 登録（表示状態） */
	@Insert("""
			INSERT INTO country_league_master (
			    country, league, team, link, del_flg,
			    register_id, register_time, update_id, update_time
			) VALUES (
			    #{country}, #{league}, #{team}, #{link,jdbcType=VARCHAR}, '0',
			    #{user}, CURRENT_TIMESTAMP, #{user}, CURRENT_TIMESTAMP
			)
			""")
	int insertTeam(TeamUploadParam p);

	/** 更新（値が入っている項目だけ） */
	@Update("""
			<script>
			UPDATE country_league_master
			<set>
			  <if test="country != null">country = #{country},</if>
			  <if test="league != null">league = #{league},</if>
			  <if test="team != null">team = #{team},</if>
			  <if test="link != null">link = #{link},</if>
			  update_id = #{user},
			  update_time = CURRENT_TIMESTAMP
			</set>
			WHERE id = CAST(#{id} AS BIGINT)
			</script>
			""")
	int updateTeam(TeamUploadParam p);

	/** 物理削除 */
	@Delete("DELETE FROM country_league_master WHERE id = CAST(#{id} AS BIGINT)")
	int deleteTeam(@Param("id") long id);

	/** 非表示（'1'）/ 再表示（'0'） */
	@Update("""
			UPDATE country_league_master
			   SET del_flg = #{delFlg}, update_id = #{user}, update_time = CURRENT_TIMESTAMP
			 WHERE id = CAST(#{id} AS BIGINT)
			""")
	int updateTeamDelFlg(@Param("id") long id, @Param("delFlg") String delFlg, @Param("user") String user);

	// =========================================================
	// 試合予定（future_master）
	// =========================================================

	/** ホーム + アウェー + 試合国及びカテゴリで seq を探す */
	@Select("""
			SELECT seq
			  FROM future_master
			 WHERE normalize(BTRIM(home_team_name), NFKC) = normalize(BTRIM(#{home}), NFKC)
			   AND normalize(BTRIM(away_team_name), NFKC) = normalize(BTRIM(#{away}), NFKC)
			   AND normalize(BTRIM(game_team_category), NFKC) = normalize(BTRIM(#{category}), NFKC)
			 ORDER BY seq
			""")
	List<Long> findFutureSeqs(@Param("home") String home, @Param("away") String away, @Param("category") String category);

	/** seq の存在確認 */
	@Select("SELECT COUNT(*) FROM future_master WHERE seq = CAST(#{seq} AS BIGINT)")
	int countFutureBySeq(@Param("seq") long seq);

	/** 登録（開始フラグは既存のスクレイピング登録と同じ 1、表示状態） */
	@Insert("""
			INSERT INTO future_master (
			    game_team_category, future_time,
			    home_rank, away_rank, home_team_name, away_team_name,
			    home_max_getting_scorer, away_max_getting_scorer,
			    home_team_home_score, home_team_home_lost,
			    away_team_home_score, away_team_home_lost,
			    home_team_away_score, home_team_away_lost,
			    away_team_away_score, away_team_away_lost,
			    game_link, data_time, start_flg, del_flg,
			    register_id, register_time, update_id, update_time
			) VALUES (
			    #{gameTeamCategory}, CAST(#{futureTime} AS timestamptz),
			    #{homeRank,jdbcType=VARCHAR}, #{awayRank,jdbcType=VARCHAR}, #{homeTeamName}, #{awayTeamName},
			    #{homeMaxGettingScorer,jdbcType=VARCHAR}, #{awayMaxGettingScorer,jdbcType=VARCHAR},
			    #{homeTeamHomeScore,jdbcType=VARCHAR}, #{homeTeamHomeLost,jdbcType=VARCHAR},
			    #{awayTeamHomeScore,jdbcType=VARCHAR}, #{awayTeamHomeLost,jdbcType=VARCHAR},
			    #{homeTeamAwayScore,jdbcType=VARCHAR}, #{homeTeamAwayLost,jdbcType=VARCHAR},
			    #{awayTeamAwayScore,jdbcType=VARCHAR}, #{awayTeamAwayLost,jdbcType=VARCHAR},
			    #{gameLink,jdbcType=VARCHAR}, CAST(#{dataTime} AS timestamptz), '1', '0',
			    #{user}, CURRENT_TIMESTAMP, #{user}, CURRENT_TIMESTAMP
			)
			""")
	int insertFuture(FutureUploadParam p);

	/** 更新（値が入っている項目だけ） */
	@Update("""
			<script>
			UPDATE future_master
			<set>
			  <if test="gameTeamCategory != null">game_team_category = #{gameTeamCategory},</if>
			  <if test="futureTime != null">future_time = CAST(#{futureTime} AS timestamptz),</if>
			  <if test="homeRank != null">home_rank = #{homeRank},</if>
			  <if test="awayRank != null">away_rank = #{awayRank},</if>
			  <if test="homeTeamName != null">home_team_name = #{homeTeamName},</if>
			  <if test="awayTeamName != null">away_team_name = #{awayTeamName},</if>
			  <if test="homeMaxGettingScorer != null">home_max_getting_scorer = #{homeMaxGettingScorer},</if>
			  <if test="awayMaxGettingScorer != null">away_max_getting_scorer = #{awayMaxGettingScorer},</if>
			  <if test="homeTeamHomeScore != null">home_team_home_score = #{homeTeamHomeScore},</if>
			  <if test="homeTeamHomeLost != null">home_team_home_lost = #{homeTeamHomeLost},</if>
			  <if test="awayTeamHomeScore != null">away_team_home_score = #{awayTeamHomeScore},</if>
			  <if test="awayTeamHomeLost != null">away_team_home_lost = #{awayTeamHomeLost},</if>
			  <if test="homeTeamAwayScore != null">home_team_away_score = #{homeTeamAwayScore},</if>
			  <if test="homeTeamAwayLost != null">home_team_away_lost = #{homeTeamAwayLost},</if>
			  <if test="awayTeamAwayScore != null">away_team_away_score = #{awayTeamAwayScore},</if>
			  <if test="awayTeamAwayLost != null">away_team_away_lost = #{awayTeamAwayLost},</if>
			  <if test="gameLink != null">game_link = #{gameLink},</if>
			  <if test="dataTime != null">data_time = CAST(#{dataTime} AS timestamptz),</if>
			  update_id = #{user},
			  update_time = CURRENT_TIMESTAMP
			</set>
			WHERE seq = CAST(#{seq} AS BIGINT)
			</script>
			""")
	int updateFuture(FutureUploadParam p);

	/** 物理削除 */
	@Delete("DELETE FROM future_master WHERE seq = CAST(#{seq} AS BIGINT)")
	int deleteFuture(@Param("seq") long seq);

	/** 非表示（'1'）/ 再表示（'0'） */
	@Update("""
			UPDATE future_master
			   SET del_flg = #{delFlg}, update_id = #{user}, update_time = CURRENT_TIMESTAMP
			 WHERE seq = CAST(#{seq} AS BIGINT)
			""")
	int updateFutureDelFlg(@Param("seq") long seq, @Param("delFlg") String delFlg, @Param("user") String user);

	/**
	 * データカテゴリ統一（2026-10-21 追加）: 同じホーム・アウェーで、カテゴリが「現在のデータカテゴリ」と同じ試合予定の
	 * カテゴリを書き換える（0 件でもよい。試合予定に無い試合もあるため）。
	 */
	@Update("""
			UPDATE future_master
			   SET game_team_category = #{next}, update_id = #{user}, update_time = CURRENT_TIMESTAMP
			 WHERE normalize(BTRIM(home_team_name), NFKC) = normalize(BTRIM(#{home}), NFKC)
			   AND normalize(BTRIM(away_team_name), NFKC) = normalize(BTRIM(#{away}), NFKC)
			   AND normalize(BTRIM(COALESCE(game_team_category, '')), NFKC) = normalize(BTRIM(#{current}), NFKC)
			""")
	int updateFutureCategoryByKey(@Param("home") String home, @Param("away") String away,
			@Param("current") String current, @Param("next") String next, @Param("user") String user);
}
