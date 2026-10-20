package dev.batch.repository.bm;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.batch.bm_b015.FileUploadStatusRow;

/**
 * ファイルアップロード状況管理（file_upload_status_manage・bm DB）B015 用
 *
 * <ul>
 *   <li>判定前（PENDING）を古い順に取り出し、判定中（PROCESSING）にしてから処理する。</li>
 *   <li>判定中にする UPDATE は「status = 'PENDING' のときだけ」にして、2 つの実行が同じ行を処理しないようにする。</li>
 *   <li>結果（正常終了・異常終了）は 1 文ずつすぐ確定する（マスタへの反映のトランザクションとは別）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Mapper
public interface FileUploadStatusBatchRepository {

	/** 判定前を古い順に取り出す */
	@Select("""
			SELECT
			    id,
			    master_name   AS masterName,
			    file_name     AS fileName,
			    csv_file_name AS csvFileName,
			    status,
			    total_count   AS totalCount,
			    login_user    AS loginUser,
			    s3_bucket     AS s3Bucket,
			    s3_key        AS s3Key
			FROM
			    file_upload_status_manage
			WHERE
			    status = 'PENDING'
			ORDER BY
			    started_at ASC,
			    id ASC
			LIMIT #{limit}
			""")
	List<FileUploadStatusRow> findPending(@Param("limit") int limit);

	/**
	 * 処理するものがあるか（判定前、または判定中のまま止まっているもの）
	 * <p>無ければバッチをスキップする（実行履歴を増やさない）。</p>
	 */
	@Select("""
			SELECT COUNT(*)
			  FROM file_upload_status_manage
			 WHERE status = 'PENDING'
			    OR (status = 'PROCESSING'
			        AND update_time < CURRENT_TIMESTAMP - make_interval(mins => #{minutes}))
			""")
	int countTargets(@Param("minutes") int minutes);

	/**
	 * 判定中にする（判定前のときだけ）
	 * @return 1 = 取れた / 0 = 他の実行が先に取った・状態が変わっていた
	 */
	@Update("""
			UPDATE file_upload_status_manage
			   SET status      = 'PROCESSING',
			       message     = #{message},
			       update_id   = #{user},
			       update_time = CURRENT_TIMESTAMP
			 WHERE id = #{id}
			   AND status = 'PENDING'
			""")
	int claimProcessing(@Param("id") long id, @Param("message") String message, @Param("user") String user);

	/** 正常終了（反映の件数と success/ の場所を記録） */
	@Update("""
			UPDATE file_upload_status_manage
			   SET status       = 'SUCCESS',
			       error_phase  = NULL,
			       message      = #{message},
			       total_count  = #{totalCount},
			       insert_count = #{insertCount},
			       update_count = #{updateCount},
			       delete_count = #{deleteCount},
			       hide_count   = #{hideCount},
			       show_count   = #{showCount},
			       error_count  = 0,
			       error_detail = NULL,
			       s3_bucket    = #{s3Bucket},
			       s3_key       = #{s3Key},
			       finished_at  = CURRENT_TIMESTAMP,
			       update_id    = #{user},
			       update_time  = CURRENT_TIMESTAMP
			 WHERE id = #{id}
			""")
	int markSuccess(@Param("id") long id,
			@Param("message") String message,
			@Param("totalCount") int totalCount,
			@Param("insertCount") int insertCount,
			@Param("updateCount") int updateCount,
			@Param("deleteCount") int deleteCount,
			@Param("hideCount") int hideCount,
			@Param("showCount") int showCount,
			@Param("s3Bucket") String s3Bucket,
			@Param("s3Key") String s3Key,
			@Param("user") String user);

	/** 異常終了（error/ の場所とエラー行を記録。反映の件数は 0 に戻す） */
	@Update("""
			UPDATE file_upload_status_manage
			   SET status       = 'ERROR',
			       error_phase  = #{errorPhase},
			       message      = #{message},
			       total_count  = #{totalCount},
			       insert_count = 0,
			       update_count = 0,
			       delete_count = 0,
			       hide_count   = 0,
			       show_count   = 0,
			       error_count  = #{errorCount},
			       error_detail = CAST(#{errorDetail,jdbcType=VARCHAR} AS jsonb),
			       s3_bucket    = #{s3Bucket,jdbcType=VARCHAR},
			       s3_key       = #{s3Key,jdbcType=VARCHAR},
			       finished_at  = CURRENT_TIMESTAMP,
			       update_id    = #{user},
			       update_time  = CURRENT_TIMESTAMP
			 WHERE id = #{id}
			""")
	int markError(@Param("id") long id,
			@Param("errorPhase") String errorPhase,
			@Param("message") String message,
			@Param("totalCount") int totalCount,
			@Param("errorCount") int errorCount,
			@Param("errorDetail") String errorDetail,
			@Param("s3Bucket") String s3Bucket,
			@Param("s3Key") String s3Key,
			@Param("user") String user);

	/**
	 * 判定中のまま止まっているもの（前の実行が途中で落ちた等）を異常終了にする
	 * <p>反映のトランザクションは確定前に落ちていれば元に戻っているが、確定後に落ちた場合もあるため、
	 *    もう一度処理はせず異常終了にして、利用者に確認してもらう。</p>
	 * @return 異常終了にした件数
	 */
	@Update("""
			UPDATE file_upload_status_manage
			   SET status       = 'ERROR',
			       error_phase  = 'DB',
			       message      = #{message},
			       finished_at  = CURRENT_TIMESTAMP,
			       update_id    = #{user},
			       update_time  = CURRENT_TIMESTAMP
			 WHERE status = 'PROCESSING'
			   AND update_time < CURRENT_TIMESTAMP - make_interval(mins => #{minutes})
			""")
	int failStaleProcessing(@Param("minutes") int minutes, @Param("message") String message, @Param("user") String user);

	/**
	 * 接続先の確認用（スキップしたときにログへ出す）
	 * <p>接続している DB・ホスト・ユーザー・search_path・テーブルのあるスキーマ・状態ごとの件数を 1 行で返す。</p>
	 */
	@Select("""
			SELECT
			    current_database()                          AS db,
			    CAST(inet_server_addr() AS text)            AS host,
			    inet_server_port()                          AS port,
			    current_user                                AS db_user,
			    current_setting('search_path')              AS search_path,
			    CAST(to_regclass('file_upload_status_manage') AS text) AS resolved_table,
			    (SELECT string_agg(table_schema, ',')
			       FROM information_schema.tables
			      WHERE table_name = 'file_upload_status_manage')        AS table_schemas,
			    (SELECT string_agg(status || '(' || length(status) || ')=' || cnt, ', ' ORDER BY status)
			       FROM (SELECT status, COUNT(*) AS cnt
			               FROM file_upload_status_manage
			              GROUP BY status) t)                            AS status_counts
			""")
	Map<String, Object> diagnose();
}
