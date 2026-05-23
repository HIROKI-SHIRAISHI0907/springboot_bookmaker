package dev.batch.repository.bm;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
              FROM ecs_scrape_task_progress
             WHERE batch_cd = #{batchCode}
               AND status IN ('REQUESTED', 'PENDING', 'PROVISIONING', 'ACTIVATING', 'RUNNING')
        )
    """)
    boolean existsRunningTask(@Param("batchCode") String batchCode);
}
