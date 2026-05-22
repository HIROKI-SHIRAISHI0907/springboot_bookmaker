package dev.batch.repository.bm;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.ExecutionHistoryRecordEntity;

/**
 * バッチ実行履歴テーブルを操作するRepository。
 */
@Mapper
public interface ExecutionHistoryBatchRepository {

    /**
     * 実行開始履歴を登録する。
     *
     * @param record 実行履歴レコード
     * @return 更新件数
     */
    @Insert("""
        INSERT INTO execution_history (
            execution_id,
            execution_type,
            execution_name,
            status,
            trigger_type,
            request_path,
            http_method,
            class_name,
            method_name,
            job_name,
            requested_by,
            trace_id,
            start_time,
            end_time,
            duration_ms,
            error_message,
            register_id,
            register_time,
            update_id,
            update_time
        ) VALUES (
            #{executionId},
            #{executionType},
            #{executionName},
            #{status},
            #{triggerType},
            #{requestPath},
            #{httpMethod},
            #{className},
            #{methodName},
            #{jobName},
            #{requestedBy},
            #{traceId},
            #{startTime},
            #{endTime},
            #{durationMs},
            #{errorMessage},
            'SYSTEM',
            CURRENT_TIMESTAMP,
            'SYSTEM',
            CURRENT_TIMESTAMP
        )
    """)
    int insertStart(ExecutionHistoryRecordEntity record);

    /**
     * ステータスのみ更新する。
     *
     * @param executionId 実行ID
     * @param status ステータス
     * @return 更新件数
     */
    @Update("""
        UPDATE execution_history
           SET status = #{status},
               update_id = 'SYSTEM',
               update_time = CURRENT_TIMESTAMP
         WHERE execution_id = #{executionId}
    """)
    int updateStatus(
            @Param("executionId") String executionId,
            @Param("status") String status);

    /**
     * 正常終了情報を更新する。
     *
     * @param executionId 実行ID
     * @param status ステータス
     * @param endTime 終了日時
     * @param durationMs 実行時間(ms)
     * @return 更新件数
     */
    @Update("""
        UPDATE execution_history
           SET status = #{status},
               end_time = #{endTime},
               duration_ms = #{durationMs},
               error_message = NULL,
               update_id = 'SYSTEM',
               update_time = CURRENT_TIMESTAMP
         WHERE execution_id = #{executionId}
    """)
    int updateSuccess(
            @Param("executionId") String executionId,
            @Param("status") String status,
            @Param("endTime") LocalDateTime endTime,
            @Param("durationMs") long durationMs);

    /**
     * 異常終了情報を更新する。
     *
     * @param executionId 実行ID
     * @param status ステータス
     * @param endTime 終了日時
     * @param durationMs 実行時間(ms)
     * @param errorMessage エラーメッセージ
     * @return 更新件数
     */
    @Update("""
        UPDATE execution_history
           SET status = #{status},
               end_time = #{endTime},
               duration_ms = #{durationMs},
               error_message = #{errorMessage},
               update_id = 'SYSTEM',
               update_time = CURRENT_TIMESTAMP
         WHERE execution_id = #{executionId}
    """)
    int updateFailure(
            @Param("executionId") String executionId,
            @Param("status") String status,
            @Param("endTime") LocalDateTime endTime,
            @Param("durationMs") long durationMs,
            @Param("errorMessage") String errorMessage);

    /**
     * 終了情報を更新する。
     *
     * <p>
     * Entity を使ってまとめて更新したい場合の汎用更新。
     * 現在の Service では未使用だが、将来の共通化用に残してよい。
     * </p>
     *
     * @param record 実行履歴レコード
     * @return 更新件数
     */
    @Update("""
        UPDATE execution_history
           SET status = #{status},
               end_time = #{endTime},
               duration_ms = #{durationMs},
               error_message = #{errorMessage},
               update_id = 'SYSTEM',
               update_time = CURRENT_TIMESTAMP
         WHERE execution_id = #{executionId}
    """)
    int updateFinished(ExecutionHistoryRecordEntity record);
}
