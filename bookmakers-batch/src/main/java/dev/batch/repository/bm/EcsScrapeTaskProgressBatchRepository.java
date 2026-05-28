package dev.batch.repository.bm;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * ECS scrape task 進捗情報Repository。
 */
@Mapper
public interface EcsScrapeTaskProgressBatchRepository {

    /**
     * 指定バッチコードに対して、稼働中または起動要求中の ECS Task が存在するかを返す。
     *
     * @param batchCode バッチコード
     * @return true: 存在する / false: 存在しない
     */
    @Select("""
        SELECT EXISTS (
            SELECT 1
              FROM (
                  SELECT status
                    FROM ecs_scrape_task_progress
                   WHERE batch_cd = #{batchCode}
                   ORDER BY register_time DESC
                   LIMIT 1
              ) latest
             WHERE latest.status IN ('REQUESTED', 'PENDING', 'PROVISIONING', 'ACTIVATING', 'RUNNING')
        )
    """)
    boolean existsRunningTask(@Param("batchCode") String batchCode);

    /**
     * 最新1件のタスクがタイムアウトしていたら FAILED に更新する。
     *
     * 対象:
     * - 指定 batchCode の最新1件
     * - status が open 系
     * - start_time（無ければ register_time）から timeoutMinutes 超過
     *
     * @param batchCode バッチコード
     * @param timeoutMinutes タイムアウト分
     * @param errorMessage 自動FAILED時のエラーメッセージ
     * @return 更新件数
     */
    @Update("""
        WITH latest AS (
            SELECT progress_id
              FROM ecs_scrape_task_progress
             WHERE batch_cd = #{batchCode}
             ORDER BY register_time DESC, update_time DESC, progress_id DESC
             LIMIT 1
        )
        UPDATE ecs_scrape_task_progress p
           SET status = 'FAILED',
               error_message = #{errorMessage},
               end_time = COALESCE(p.end_time, CURRENT_TIMESTAMP),
               update_id = 'SYSTEM',
               update_time = CURRENT_TIMESTAMP
          FROM latest
         WHERE p.progress_id = latest.progress_id
           AND p.status IN ('REQUESTED', 'PENDING', 'PROVISIONING', 'ACTIVATING', 'RUNNING')
           AND COALESCE(p.start_time, p.register_time)
               <= CURRENT_TIMESTAMP - ((#{timeoutMinutes} || ' minutes')::interval)
    """)
    int failLatestTimedOutTask(
            @Param("batchCode") String batchCode,
            @Param("timeoutMinutes") int timeoutMinutes,
            @Param("errorMessage") String errorMessage
    );
}
