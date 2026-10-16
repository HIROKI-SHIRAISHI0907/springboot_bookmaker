package dev.application.domain.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 延期の試合の予定（future_master）を探す（FutureStartFlgService。2026-10 追加）。
 *
 * <p>FutureMasterRepository#findPostponeTargets との違い</p>
 * <ul>
 *   <li>mid が一致する場合も、試合開始日時が基準時刻の前後1日以内のものだけにする。
 *       延期の試合が別の日に変わり（B005 が future_time を上書き）、delay_postpone に古い延期が残っていても、
 *       新しい日の予定を延期扱いにしない（start_flg を戻さない・データを消さない）。</li>
 *   <li>データの退避・削除に使うため、seq だけでなく試合開始日時・チーム名・game_link も返す。</li>
 * </ul>
 * <p>future_time は日本時間の壁時計の timestamp。基準時刻も日本時間の timestamp にして比べる。</p>
 * @author shiraishitoshio
 */
@Mapper
public interface FuturePostponeRepository {

	/**
	 * 延期になった試合の予定。
	 *
	 * @param homeTeamName ホーム（delay_postpone の表記）
	 * @param awayTeamName アウェー（delay_postpone の表記）
	 * @param matchId mid（null 可）
	 * @param baseTime 基準時刻（元の試合開始日時。無ければ延期を検知した日時。"yyyy-MM-dd HH:mm:ss"・日本時間）。null なら現在時刻
	 * @return seq / futureTime（"yyyy-MM-dd HH:mm:ss"）/ homeTeamName / awayTeamName / gameLink
	 */
	@Select("""
			WITH b AS (
				SELECT COALESCE(
					CAST(NULLIF(BTRIM(#{baseTime,jdbcType=VARCHAR}), '') AS timestamp),
					CAST(now() AT TIME ZONE 'Asia/Tokyo' AS timestamp)
				) AS base_time
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
				f.future_time BETWEEN b.base_time - INTERVAL '1 day' AND b.base_time + INTERVAL '1 day'
				AND (
					(NULLIF(BTRIM(#{matchId,jdbcType=VARCHAR}), '') IS NOT NULL
					 AND substring(f.game_link from 'mid=([^&]+)') = BTRIM(#{matchId,jdbcType=VARCHAR}))
				 OR (
					normalize(f.home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
					AND normalize(f.away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
				 )
				)
			ORDER BY f.seq
			""")
	List<Row> findPostponeTargetRows(
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("matchId") String matchId,
			@Param("baseTime") String baseTime);

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
