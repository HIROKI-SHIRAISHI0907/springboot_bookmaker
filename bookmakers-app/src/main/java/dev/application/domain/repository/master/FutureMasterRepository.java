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
 * <p>future_time は timestamptz（時刻そのもの。psql など UTC の接続では「+00」付きで表示される）。
 * 現在時刻とは {@code now()} とそのまま比べる。日本時間の文字列（record_time 等）は
 * {@code CAST(x AS timestamp) AT TIME ZONE 'Asia/Tokyo'} で timestamptz にしてから比べる。</p>
 *
 * <p>2026-10-17 緊急修正: 以前の版は future_time を timestamp（日本時間の壁時計）と誤認し、
 * {@code future_time < CAST(now() AT TIME ZONE 'Asia/Tokyo' AS timestamp)} で比べていた。
 * timestamptz と timestamp の比較は、timestamp 側を接続のタイムゾーン（ECS は UTC）で解釈するため、
 * 試合開始の9時間前に start_flg = 1 になっていた（管理画面で開始前の試合が「ライブ」になった）。
 * 基準時刻（日本時間の文字列）も同じ理由で、明示的に日本時間として timestamptz に直す。</p>
 *
 * <p>2026-10 追加（DataCategoryService のラウンド取得。batch の B010 と同じ考え方）</p>
 * <ul>
 *   <li>findGameTeamCategoryByMid: game_link の mid が一致する予定のカテゴリ。</li>
 *   <li>findGameTeamCategoryNearTime / updateGameTeamCategoryNearTime: 基準時刻（その行の record_time）の前後1日の同じカードの予定だけ。</li>
 * </ul>
 * findGameTeamCategoryByTeams は並び順・期間の指定が無く、去年の同じカードのラウンドが返ることがあるため data_category の決定には使わない。
 *
 * <p>2026-10 修正（FutureStartFlgService）</p>
 * <ul>
 *   <li>findStartFlgTarget を追加: start_flg を 1 にする「同じ試合の予定」を mid 優先・試合開始日時が一番近い1件に絞る
 *       （findOnlyTeam はチーム名の完全一致だけで、別の日の同じカードを返すことがあった）。</li>
 *   <li>updateFutureTimeFlg: start_flg = '0' の行だけ更新。比較は future_time &lt; now()（2026-10-17 修正）。</li>
 *   <li>基準時刻の前後1日の条件（NearTime / ForMatch）: 基準時刻（日本時間の文字列）を日本時間として timestamptz に直して比べる。</li>
 *   <li>延期: findPostponeTargets / resetStartFlg を追加。updateFutureTimeFlg に延期中の試合の除外（excludeSeqs）を追加。</li>
 * </ul>
 */
@Mapper
public interface FutureMasterRepository {

	@Update("""
			  UPDATE future_master
			  SET start_flg = #{startFlg}
			  WHERE seq = CAST(#{seq} AS INTEGER)
			""")
	int updateStartFlg(@Param("seq") String seq, @Param("startFlg") String startFlg);

	/**
	 * 同じカードの未開始の予定（チーム名の完全一致・期間の指定なし）。
	 * ※ 別の日の同じカードが返ることがあるため、start_flg の更新には {@link #findStartFlgTarget} を使う。
	 */
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

	/**
	 * start_flg を 1 にする「同じ試合の予定」（start_flg = '0' のもの1件）。
	 * <ol>
	 *   <li>game_link の mid が matchId と一致する予定を優先</li>
	 *   <li>無ければ同じカード（NFKC 正規化）で、基準時刻（記録時刻。空なら現在時刻。日本時間）の前後1日の予定</li>
	 *   <li>複数あれば試合開始日時が基準時刻に一番近いもの</li>
	 * </ol>
	 *
	 * @param homeTeamName ホーム
	 * @param awayTeamName アウェー
	 * @param matchId マッチID（null 可）
	 * @param baseTime 基準時刻（"yyyy-MM-dd HH:mm:ss" 等。日本時間。null 可）
	 * @return 予定（seq・future_time）。無ければ null
	 */
	@Select("""
			WITH b AS (
				SELECT COALESCE(
					(CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp) AT TIME ZONE 'Asia/Tokyo'),
					now()
				) AS base_time
			)
			SELECT
				f.seq,
				f.future_time AS futureTime
			FROM
				future_master f, b
			WHERE
				f.start_flg = '0'
				AND (
					(NULLIF(BTRIM(#{matchId,jdbcType=VARCHAR}), '') IS NOT NULL
					 AND substring(f.game_link from 'mid=([^&]+)') = BTRIM(#{matchId,jdbcType=VARCHAR}))
				 OR (
					normalize(f.home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
					AND normalize(f.away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
					AND f.future_time BETWEEN b.base_time - INTERVAL '1 day' AND b.base_time + INTERVAL '1 day'
				 )
				)
			ORDER BY
				(NULLIF(BTRIM(#{matchId,jdbcType=VARCHAR}), '') IS NOT NULL
				 AND substring(f.game_link from 'mid=([^&]+)') = BTRIM(#{matchId,jdbcType=VARCHAR})) DESC,
				ABS(EXTRACT(EPOCH FROM (f.future_time - b.base_time))),
				f.seq DESC
			LIMIT 1
			""")
	FutureEntity findStartFlgTarget(
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("matchId") String matchId,
			@Param("baseTime") String baseTime);

	/**
	 * 試合開始日時を過ぎた未開始の予定を start_flg = #{startFlg} にする。
	 * future_time は timestamptz なので now() とそのまま比べる（接続のタイムゾーンに左右されない）。
	 * 延期中の試合（excludeSeqs）は対象外（延期の試合は開始するまで start_flg = 0 のまま）。
	 *
	 * @param startFlg 試合開始フラグ
	 * @param excludeSeqs 対象外にする seq（延期中の試合。null・空なら除外なし）
	 */
	@Update("""
			<script>
			UPDATE future_master
			SET start_flg = #{startFlg}
			WHERE start_flg = '0'
			  AND future_time &lt; now()
			  <if test="excludeSeqs != null and excludeSeqs.size() > 0">
			  AND seq NOT IN
			    <foreach collection="excludeSeqs" item="s" open="(" separator="," close=")">CAST(#{s} AS INTEGER)</foreach>
			  </if>
			</script>
			""")
	int updateFutureTimeFlg(@Param("startFlg") String startFlg, @Param("excludeSeqs") List<String> excludeSeqs);

	/**
	 * 延期になった試合の予定の seq（start_flg は問わない）。
	 * game_link の mid が一致する予定、または同じカード（NFKC 正規化）で基準時刻（元の試合開始日時。空なら現在時刻。日本時間）の前後1日の予定。
	 *
	 * @param homeTeamName ホーム
	 * @param awayTeamName アウェー
	 * @param matchId マッチID（null 可）
	 * @param baseTime 基準時刻（"yyyy-MM-dd HH:mm:ss"。日本時間。null 可）
	 * @return seq の一覧
	 */
	@Select("""
			WITH b AS (
				SELECT COALESCE(
					(CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp) AT TIME ZONE 'Asia/Tokyo'),
					now()
				) AS base_time
			)
			SELECT
				CAST(f.seq AS VARCHAR)
			FROM
				future_master f, b
			WHERE
				(NULLIF(BTRIM(#{matchId,jdbcType=VARCHAR}), '') IS NOT NULL
				 AND substring(f.game_link from 'mid=([^&]+)') = BTRIM(#{matchId,jdbcType=VARCHAR}))
			 OR (
				normalize(f.home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
				AND normalize(f.away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
				AND f.future_time BETWEEN b.base_time - INTERVAL '1 day' AND b.base_time + INTERVAL '1 day'
			 )
			""")
	List<String> findPostponeTargets(
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("matchId") String matchId,
			@Param("baseTime") String baseTime);

	/**
	 * 延期になった試合の予定を start_flg = 0（未開始）に戻す。
	 *
	 * @param seqs 対象の seq
	 * @return 更新件数（既に 0 の行は数えない）
	 */
	@Update("""
			<script>
			UPDATE future_master
			SET start_flg = '0'
			WHERE start_flg &lt;&gt; '0'
			  AND seq IN
			    <foreach collection="seqs" item="s" open="(" separator="," close=")">CAST(#{s} AS INTEGER)</foreach>
			</script>
			""")
	int resetStartFlg(@Param("seqs") List<String> seqs);

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

	/**
	 * 現在時刻（日本時間）の前後1日の同じカードの予定の game_team_category を更新する。
	 */
	@Update("""
			UPDATE future_master
			SET	game_team_category = #{dataCategory}
			WHERE
				normalize(home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
				AND normalize(away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
				AND future_time BETWEEN now() - INTERVAL '1 day'
				                    AND now() + INTERVAL '1 day'
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
	 * 基準時刻（record_time。空なら現在時刻。日本時間）の前後1日の同じカードの予定の game_team_category
	 * （試合開始時刻が基準時刻に一番近いもの。仮の値 XXX は除く）。無ければ null。
	 */
	@Select("""
			WITH b AS (
				SELECT COALESCE(
					(CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp) AT TIME ZONE 'Asia/Tokyo'),
					now()
				) AS base_time
			)
			SELECT
				f.game_team_category
			FROM
				future_master f, b
			WHERE
				normalize(f.home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
				AND normalize(f.away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
				AND f.game_team_category IS NOT NULL
				AND BTRIM(f.game_team_category) <> ''
				AND f.game_team_category !~* '^\\s*XXX\\s*:'
				AND f.future_time BETWEEN b.base_time - INTERVAL '1 day' AND b.base_time + INTERVAL '1 day'
			ORDER BY ABS(EXTRACT(EPOCH FROM (f.future_time - b.base_time)))
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
				AND future_time BETWEEN COALESCE((CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp) AT TIME ZONE 'Asia/Tokyo'), now()) - INTERVAL '1 day'
				                    AND COALESCE((CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp) AT TIME ZONE 'Asia/Tokyo'), now()) + INTERVAL '1 day'
				AND game_team_category IS DISTINCT FROM #{dataCategory}
		""")
	int updateGameTeamCategoryNearTime(
			@Param("dataCategory") String dataCategory,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("baseTime") String baseTime);

	// ===================== 2026-10 追加3 =====================

	/**
	 * 同じ試合の予定（game_link の mid が一致、または同じカードで基準時刻の前後1日）の game_team_category を更新する。
	 * 既に「国: リーグ - ラウンド N」（試合ページのパンくず由来）が入っている行は書き換えない
	 * （国なし・ラウンドなし・仮の値 XXX の行だけ直す）。
	 */
	@Update("""
			UPDATE future_master
			SET	game_team_category = #{dataCategory}
			WHERE
				(
					(NULLIF(BTRIM(#{matchId,jdbcType=VARCHAR}), '') IS NOT NULL
					 AND substring(game_link from 'mid=([^&]+)') = #{matchId,jdbcType=VARCHAR})
				 OR (
					normalize(home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
					AND normalize(away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
					AND future_time BETWEEN COALESCE((CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp) AT TIME ZONE 'Asia/Tokyo'), now()) - INTERVAL '1 day'
					                    AND COALESCE((CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp) AT TIME ZONE 'Asia/Tokyo'), now()) + INTERVAL '1 day'
				 )
				)
				AND (
					game_team_category IS NULL
				 OR game_team_category !~ '^\\s*[^:：]+[:：]\\s*\\S'
				 OR game_team_category ~* '^\\s*XXX\\s*:'
				 OR game_team_category !~ 'ラウンド\\s*[1-9]'
				)
				AND game_team_category IS DISTINCT FROM #{dataCategory}
		""")
	int updateGameTeamCategoryForMatch(
			@Param("dataCategory") String dataCategory,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("matchId") String matchId,
			@Param("baseTime") String baseTime);

}
