package dev.batch.repository.master;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 延期の試合の日程変更（B005。2026-10 追加）。
 *
 * <p>future_time は日本時間の壁時計の timestamp。新しい試合開始日時は FutureMasterRepository#insert と同じ式で入れる。</p>
 * @author shiraishitoshio
 */
@Mapper
public interface FutureRescheduleRepository {

	/**
	 * 延期になった試合の予定（まだ始まっていないもの1件）。
	 * <ol>
	 *   <li>game_link の mid が一致する予定を優先</li>
	 *   <li>無ければ同じカード（NFKC）</li>
	 *   <li>どちらも試合開始日時が基準時刻（delay_postpone の元の試合開始日時、無ければ延期を検知した日時）の前後1日</li>
	 * </ol>
	 *
	 * @return seq / futureTime（"yyyy-MM-dd HH:mm:ss"）/ gameLink。無ければ null
	 */
	@Select("""
			WITH b AS (
				SELECT CAST(#{baseTime} AS timestamp) AS base_time
			)
			SELECT
				CAST(f.seq AS VARCHAR) AS seq,
				to_char(f.future_time, 'YYYY-MM-DD HH24:MI:SS') AS futureTime,
				f.home_team_name AS homeTeamName,
				f.away_team_name AS awayTeamName,
				f.game_link AS gameLink
			FROM
				future_master f, b
			WHERE
				f.start_flg = '0'
				AND f.future_time BETWEEN b.base_time - INTERVAL '1 day' AND b.base_time + INTERVAL '1 day'
				AND (
					(NULLIF(BTRIM(#{mid,jdbcType=VARCHAR}), '') IS NOT NULL
					 AND substring(f.game_link from 'mid=([^&]+)') = BTRIM(#{mid,jdbcType=VARCHAR}))
				 OR (
					normalize(f.home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
					AND normalize(f.away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
				 )
				)
			ORDER BY
				(NULLIF(BTRIM(#{mid,jdbcType=VARCHAR}), '') IS NOT NULL
				 AND substring(f.game_link from 'mid=([^&]+)') = BTRIM(#{mid,jdbcType=VARCHAR})) DESC,
				ABS(EXTRACT(EPOCH FROM (f.future_time - b.base_time))),
				f.seq DESC
			LIMIT 1
			""")
	Row findRescheduleTarget(
			@Param("mid") String mid,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("baseTime") String baseTime);

	/**
	 * 延期の試合の予定を新しい日時に変える（start_flg は 0 のまま）。
	 * <ul>
	 *   <li>新しい日時が今の日時と同じなら更新しない（日程が変わっていない＝まだ延期のまま）。</li>
	 *   <li>新しい日時が今の日時から60日を超えて離れていたら更新しない（同じカードの別の試合を誤って結び付けないため）。</li>
	 *   <li>新しい game_link があれば置き換える（試合ページが変わった場合、以降は新しい mid で突き合わせる）。</li>
	 * </ul>
	 *
	 * @return 更新件数（0 = 更新しなかった）
	 */
	@Update("""
			WITH n AS (
				SELECT (CAST(NULLIF(BTRIM(CAST(#{futureTime} AS text)), '') AS timestamp) AT TIME ZONE 'Asia/Tokyo') AS new_time
			)
			UPDATE future_master f
			SET
				future_time = n.new_time,
				game_link = COALESCE(NULLIF(BTRIM(#{gameLink,jdbcType=VARCHAR}), ''), f.game_link),
				start_flg = '0',
				update_id = 'SYSTEM',
				update_time = CURRENT_TIMESTAMP
			FROM n
			WHERE
				f.seq = CAST(#{seq} AS INTEGER)
				AND f.start_flg = '0'
				AND n.new_time IS NOT NULL
				AND f.future_time IS DISTINCT FROM n.new_time
				AND n.new_time BETWEEN f.future_time - INTERVAL '60 days' AND f.future_time + INTERVAL '60 days'
			""")
	int updateRescheduledTime(
			@Param("seq") String seq,
			@Param("futureTime") Object futureTime,
			@Param("gameLink") String gameLink);

	/** 延期の試合の予定1件 */
	class Row {
		private String seq;
		/** 試合開始日時（"yyyy-MM-dd HH:mm:ss"・日本時間の壁時計） */
		private String futureTime;
		private String homeTeamName;
		private String awayTeamName;
		private String gameLink;

		public String getSeq() {
			return seq;
		}

		public void setSeq(String seq) {
			this.seq = seq;
		}

		public String getFutureTime() {
			return futureTime;
		}

		public void setFutureTime(String futureTime) {
			this.futureTime = futureTime;
		}

		public String getHomeTeamName() {
			return homeTeamName;
		}

		public void setHomeTeamName(String homeTeamName) {
			this.homeTeamName = homeTeamName;
		}

		public String getAwayTeamName() {
			return awayTeamName;
		}

		public void setAwayTeamName(String awayTeamName) {
			this.awayTeamName = awayTeamName;
		}

		public String getGameLink() {
			return gameLink;
		}

		public void setGameLink(String gameLink) {
			this.gameLink = gameLink;
		}
	}
}
