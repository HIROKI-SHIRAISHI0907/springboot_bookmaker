package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.FutureEntity;

/**
 * future_master の Repository（batch）
 *
 * <p>2026-10 追加</p>
 * <ul>
 *   <li>findGameTeamCategoryNearTime / updateGameTeamCategoryNearTime:
 *       基準時刻（試合の記録時刻。空なら現在時刻 JST）の前後1日の同じカードの予定だけを見る・書き換える。
 *       DataCategoryBatchService で使う（去年・来年の同じカードの予定を混ぜない）。</li>
 * </ul>
 * 既存のメソッドは変更していない。
 */
@Mapper
public interface FutureMasterRepository {

	@Insert("""
			    INSERT INTO future_master (
			        game_team_category,
			        future_time,
			        home_rank,
			        away_rank,
			        home_team_name,
			        away_team_name,
					home_max_getting_scorer,
					away_max_getting_scorer,
					home_team_home_score,
					home_team_home_lost,
					away_team_home_score,
					away_team_home_lost,
					home_team_away_score,
					home_team_away_lost,
					away_team_away_score,
					away_team_away_lost,
					game_link,
					data_time,
					start_flg,
					register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			        #{gameTeamCategory},
			        COALESCE(
                		(CAST(NULLIF(BTRIM(CAST(#{futureTime} AS text)), '') AS timestamp) AT TIME ZONE 'Asia/Tokyo'),
                		(CAST(NULLIF(BTRIM(CAST(#{dataTime}   AS text)), '') AS timestamp) AT TIME ZONE 'Asia/Tokyo'),
                		CURRENT_TIMESTAMP
            		),
			        #{homeRank},
			        #{awayRank},
			        #{homeTeamName},
			        #{awayTeamName},
			        #{homeMaxGettingScorer},
			        #{awayMaxGettingScorer},
			        #{homeTeamHomeScore},
			        #{homeTeamHomeLost},
			        #{awayTeamHomeScore},
			        #{awayTeamHomeLost},
			        #{homeTeamAwayScore},
			        #{homeTeamAwayLost},
			        #{awayTeamAwayScore},
			        #{awayTeamAwayLost},
			        #{gameLink},
			        COALESCE(
						(CAST(NULLIF(BTRIM(CAST(#{dataTime} AS text)), '') AS timestamp) AT TIME ZONE 'Asia/Tokyo'),
						NULL
					),
			        1,
			        'SYSTEM',
			        CURRENT_TIMESTAMP,
			        'SYSTEM',
			        CURRENT_TIMESTAMP)
			""")
	int insert(FutureEntity entity);

	@Select("""
			    SELECT
			        COUNT(*)
			    FROM
			    	future_master
			    WHERE
			        normalize(home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
				AND normalize(away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC);
			""")
	int findDataCount(FutureEntity entity);

	@Select("""
	        SELECT
	            COUNT(*)
	        FROM
	            future_master
	        WHERE
	            game_link IS NOT NULL
	            AND substring(game_link from 'mid=([^&]+)') = #{mid}
	        """)
	int findCountByMid(@Param("mid") String mid);

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
			        normalize(home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
				AND normalize(away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC);
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

	@Delete("""
			DELETE
			FROM future_master
			WHERE game_team_category LIKE CONCAT(#{gameTeamCategoryLike}, '%')
			""")
	int deleteByDataCategory(@Param("gameTeamCategoryLike") String gameTeamCategoryLike);

	@Select("""
			SELECT
				game_team_category
			FROM
				future_master
			WHERE
				home_team_name = #{homeTeamName}
				AND away_team_name = #{awayTeamName}
				AND game_team_category IS NOT NULL
				AND BTRIM(game_team_category) <> ''
			ORDER BY
				future_time DESC NULLS LAST,
				seq DESC
			LIMIT 1
		""")
	String findLatestGameTeamCategoryByTeams(
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

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
	String findGameTeamCategoryByBothTeams(
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

	@Select("""
			SELECT
 				game_link
 			FROM
 				future_master
			WHERE
				normalize(home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
				AND normalize(away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
			LIMIT 1
		""")
	String findGameLinkWithoutFinishedCategoryByTeamsWithTeam(
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	@Select("""
			SELECT
 				home_team_name AS homeTeamName,
 				away_team_name AS awayTeamName,
 				future_time AS futureTime
 			FROM
 				future_master
			WHERE
				(future_time > #{todayStart}::timestamptz
				AND future_time <= #{todayEnd}::timestamptz)
				AND start_flg = '1'
		""")
	List<FutureEntity> findTodayFinData(
			@Param("todayStart") String todayStart,
			@Param("todayEnd") String todayEnd);

	@Select("""
			SELECT
				game_team_category AS gameTeamCategory,
 				home_team_name AS homeTeamName,
 				away_team_name AS awayTeamName
 			FROM
 				future_master
			WHERE
				(future_time > #{todayStart}::timestamptz
				AND future_time <= #{todayEnd}::timestamptz)
				AND start_flg = '1'
		""")
	List<FutureEntity> findWeeksData(
			@Param("todayStart") String todayStart,
			@Param("todayEnd") String todayEnd);

	@Select("""
			SELECT
				game_team_category
			FROM
				future_master
			WHERE
				(
					normalize(home_team_name, NFKC) = normalize(#{team}, NFKC)
				OR
					normalize(away_team_name, NFKC) = normalize(#{team}, NFKC)
				)
				AND
				game_team_category IS NOT NULL
			LIMIT 1
		""")
	String findGameTeamCategoryByTeams(
			@Param("team") String team);

	@Update("""
	        UPDATE future_master
	        SET game_team_category = #{dataCategory}
	        WHERE normalize(home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
	          AND normalize(away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
	          AND (game_team_category IS NULL OR game_team_category NOT LIKE '%ラウンド%')
	        """)
	int updateByGameTeamCategoryWithNotRound(
	        @Param("dataCategory") String dataCategory,
	        @Param("homeTeamName") String homeTeamName,
	        @Param("awayTeamName") String awayTeamName);

	// ===================== 2026-10 追加 =====================

	/**
	 * 基準時刻の前後1日の同じカードの予定の game_team_category（試合開始時刻が基準時刻に一番近いもの）。
	 * 仮の値「XXX: YYY …」は除く。無ければ null。
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
