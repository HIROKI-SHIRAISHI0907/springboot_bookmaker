package dev.application.domain.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.FutureEntity;

/**
 * future_master の Repository（BM_M001 ほか）
 *
 * <p>2026-10 追加（DataCategoryService のラウンド取得。batch の B010 と同じ考え方）</p>
 * <ul>
 *   <li>findGameTeamCategoryByMid: game_link の mid が一致する予定のカテゴリ。</li>
 *   <li>findGameTeamCategoryNearTime / updateGameTeamCategoryNearTime: 基準時刻（その行の record_time）の前後1日の同じカードの予定だけ。</li>
 * </ul>
 * findGameTeamCategoryByTeams は並び順・期間の指定が無く、去年の同じカードのラウンドが返ることがあるため data_category の決定には使わない。
 */
@Mapper
public interface FutureMasterRepository {

	@Update("""
			  UPDATE future_master
			  SET start_flg = #{startFlg}
			  WHERE seq = CAST(#{seq} AS INTEGER)
			""")
	int updateStartFlg(@Param("seq") String seq, @Param("startFlg") String startFlg);

	@Select("""
			    SELECT
			        seq
			    FROM
			    	future_master
			    WHERE
			        home_team_name = #{homeTeamName} AND
			        away_team_name = #{awayTeamName}
			       AND start_flg = '0'
			""")
	List<FutureEntity> findOnlyTeam(FutureEntity entity);

	@Update("""
			UPDATE future_master
			SET start_flg = #{startFlg}
			WHERE future_time < now()
			""")
	int updateFutureTimeFlg(@Param("startFlg") String startFlg);

	@Select("""
			    SELECT
			        COUNT(*)
			    FROM
			    	future_master;
			""")
	int findAll();

	/**
	 * BM_M097 用:
	 * future_master にある future_time の JST日付一覧を取得
	 */
	@Select("""
			SELECT
			    seq,
			    home_team_name AS homeTeamName,
			    away_team_name AS awayTeamName,
			    future_time AS futureTime
			FROM future_master
			WHERE future_time IS NOT NULL
			  AND home_team_name IS NOT NULL
			  AND away_team_name IS NOT NULL
			ORDER BY seq
			""")
	List<FutureEntity> findFutureDatesForManualStat();

	/**
	 * 同じカードの game_team_category（期間・並び順の指定なし）。
	 * ※ data_category の決定には使わない（findGameTeamCategoryByMid / findGameTeamCategoryNearTime を使う）。
	 */
	@Select("""
			SELECT
				game_team_category
			FROM
				future_master
			WHERE
				normalize(home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
				AND normalize(away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
				AND game_team_category IS NOT NULL
				AND BTRIM(game_team_category) <> ''
			LIMIT 1
		""")
	String findGameTeamCategoryByTeams(
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	@Update("""
			UPDATE future_master
			SET	game_team_category = #{dataCategory}
			WHERE
				normalize(home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
				AND normalize(away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
				AND future_time BETWEEN now() - INTERVAL '1 day' AND now() + INTERVAL '1 day'
				AND game_team_category IS DISTINCT FROM #{dataCategory}
		""")
	int updateGameTeamCategoryByTeamsNearNow(
			@Param("dataCategory") String dataCategory,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	@Update("""
			UPDATE future_master
			SET	game_team_category = #{dataCategory}
			WHERE
				normalize(home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
				AND normalize(away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC);
		""")
	int updateGameTeamCategoryByTeams(
			@Param("dataCategory") String dataCategory,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	// ===================== 2026-10 追加 =====================

	/**
	 * game_link の mid が matchId と一致する予定の game_team_category（仮の値 XXX は除く）。
	 * ラウンドを含むものを優先。無ければ null。
	 */
	@Select("""
			SELECT
				game_team_category
			FROM
				future_master
			WHERE
				NULLIF(BTRIM(game_link), '') IS NOT NULL
				AND substring(game_link from 'mid=([^&]+)') = #{matchId}
				AND game_team_category IS NOT NULL
				AND BTRIM(game_team_category) <> ''
				AND game_team_category !~* '^\\s*XXX\\s*:'
			ORDER BY
				(game_team_category LIKE '%ラウンド%') DESC,
				seq DESC
			LIMIT 1
		""")
	String findGameTeamCategoryByMid(@Param("matchId") String matchId);

	/**
	 * 基準時刻（record_time。空なら現在時刻 JST）の前後1日の同じカードの予定の game_team_category
	 * （試合開始時刻が基準時刻に一番近いもの。仮の値 XXX は除く）。無ければ null。
	 */
	@Select("""
			SELECT
				game_team_category
			FROM
				future_master
			WHERE
				normalize(home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
				AND normalize(away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
				AND game_team_category IS NOT NULL
				AND BTRIM(game_team_category) <> ''
				AND game_team_category !~* '^\\s*XXX\\s*:'
				AND future_time BETWEEN (COALESCE(CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp), CAST(now() AT TIME ZONE 'Asia/Tokyo' AS timestamp)) AT TIME ZONE 'Asia/Tokyo') - INTERVAL '1 day' AND (COALESCE(CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp), CAST(now() AT TIME ZONE 'Asia/Tokyo' AS timestamp)) AT TIME ZONE 'Asia/Tokyo') + INTERVAL '1 day'
			ORDER BY ABS(EXTRACT(EPOCH FROM (future_time - (COALESCE(CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp), CAST(now() AT TIME ZONE 'Asia/Tokyo' AS timestamp)) AT TIME ZONE 'Asia/Tokyo'))))
			LIMIT 1
		""")
	String findGameTeamCategoryNearTime(
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("baseTime") String baseTime);

	/**
	 * 基準時刻の前後1日の同じカードの予定の game_team_category を更新する（それ以外の時期の同じカードは触らない）。
	 */
	@Update("""
			UPDATE future_master
			SET	game_team_category = #{dataCategory}
			WHERE
				normalize(home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
				AND normalize(away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
				AND future_time BETWEEN (COALESCE(CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp), CAST(now() AT TIME ZONE 'Asia/Tokyo' AS timestamp)) AT TIME ZONE 'Asia/Tokyo') - INTERVAL '1 day' AND (COALESCE(CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp), CAST(now() AT TIME ZONE 'Asia/Tokyo' AS timestamp)) AT TIME ZONE 'Asia/Tokyo') + INTERVAL '1 day'
				AND game_team_category IS DISTINCT FROM #{dataCategory}
		""")
	int updateGameTeamCategoryNearTime(
			@Param("dataCategory") String dataCategory,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("baseTime") String baseTime);

}