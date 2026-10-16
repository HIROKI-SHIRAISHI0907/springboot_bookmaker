package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 延期になった試合のデータの退避・削除（BM_M001 の PostponedDataPurger から使う。2026-10 追加）。
 *
 * <h2>対象の行（static_data）</h2>
 * <ul>
 *   <li>mid がある場合: match_id = mid で、record_time が元の試合開始日時（baseTime）の前後1日以内</li>
 *   <li>または、同じカード（NFKC）で record_time が baseTime の1時間前〜6時間後
 *       （画面の件数・FutureMatchDataStatusRepository と同じ範囲）</li>
 * </ul>
 * <p>baseTime は future_master.future_time（日本時間の壁時計 "yyyy-MM-dd HH:mm:ss"）。</p>
 *
 * <h2>退避先</h2>
 * <p>static_data_postponed / real_data_process_postponed（元テーブルと同じ列 ＋ purged_at・purge_reason。DDL: postponed_backup_tables.sql）。
 * 列の並びが元テーブルと同じ前提で {@code SELECT s.*} をそのまま入れるため、元テーブルに列を追加したら退避テーブルにも同じ位置に追加すること。</p>
 * @author shiraishitoshio
 */
@Mapper
public interface PostponedDataPurgeRepository {

	/** 対象の行の条件（static_data s） */
	String MATCH_COND = """
			(
			  (NULLIF(BTRIM(#{mid,jdbcType=VARCHAR}), '') IS NOT NULL
			   AND s.match_id = BTRIM(#{mid,jdbcType=VARCHAR})
			   AND s.record_time BETWEEN CAST(#{baseTime} AS timestamp) - INTERVAL '1 day'
			                         AND CAST(#{baseTime} AS timestamp) + INTERVAL '1 day')
			  OR (
			   normalize(s.home_team_name, NFKC) = normalize(#{homeTeamName}, NFKC)
			   AND normalize(s.away_team_name, NFKC) = normalize(#{awayTeamName}, NFKC)
			   AND s.record_time BETWEEN CAST(#{baseTime} AS timestamp) - INTERVAL '1 hour'
			                         AND CAST(#{baseTime} AS timestamp) + INTERVAL '6 hours')
			)
			""";

	/**
	 * 終了済の行の数（終了済があれば延期ではないので消さない）。
	 */
	@Select("""
			SELECT COUNT(*)
			FROM static_data s
			WHERE REPLACE(BTRIM(normalize(COALESCE(s.times, ''), NFKC)), ' ', '') = '終了済'
			  AND """ + MATCH_COND)
	int countFinished(
			@Param("mid") String mid,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("baseTime") String baseTime);

	/**
	 * 対象の行の match_id（real_data_process の削除に使う）。
	 */
	@Select("""
			SELECT DISTINCT BTRIM(s.match_id)
			FROM static_data s
			WHERE NULLIF(BTRIM(s.match_id), '') IS NOT NULL
			  AND """ + MATCH_COND)
	List<String> findMatchIds(
			@Param("mid") String mid,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("baseTime") String baseTime);

	/**
	 * 対象の行を static_data_postponed にコピーする。
	 *
	 * @return コピーした行数
	 */
	@Insert("""
			INSERT INTO static_data_postponed
			SELECT s.*, CURRENT_TIMESTAMP, CAST(#{reason} AS text)
			FROM static_data s
			WHERE """ + MATCH_COND)
	int backupStaticData(
			@Param("mid") String mid,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("baseTime") String baseTime,
			@Param("reason") String reason);

	/**
	 * 対象の行を static_data から削除する。
	 *
	 * @return 削除した行数
	 */
	@Delete("""
			DELETE FROM static_data s
			WHERE """ + MATCH_COND)
	int deleteStaticData(
			@Param("mid") String mid,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("baseTime") String baseTime);

	/**
	 * real_data_process の行を real_data_process_postponed にコピーする。
	 *
	 * @return コピーした行数
	 */
	@Insert("""
			<script>
			INSERT INTO real_data_process_postponed
			SELECT r.*, CURRENT_TIMESTAMP, CAST(#{reason} AS text)
			FROM real_data_process r
			WHERE r.match_id IN
			  <foreach collection="matchIds" item="m" open="(" separator="," close=")">#{m}</foreach>
			</script>
			""")
	int backupRealDataProcess(@Param("matchIds") List<String> matchIds, @Param("reason") String reason);

	/**
	 * real_data_process の行を削除する。
	 *
	 * @return 削除した行数
	 */
	@Delete("""
			<script>
			DELETE FROM real_data_process
			WHERE match_id IN
			  <foreach collection="matchIds" item="m" open="(" separator="," close=")">#{m}</foreach>
			</script>
			""")
	int deleteRealDataProcess(@Param("matchIds") List<String> matchIds);
}
