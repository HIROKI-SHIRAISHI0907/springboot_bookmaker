package dev.web.repository.bm;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a009.EcsScrapeTaskProgressRecordEntity;


@Repository
public class EcsScrapeTaskProgressWebRepository {

    private final NamedParameterJdbcTemplate bmJdbcTemplate;

    public EcsScrapeTaskProgressWebRepository(
            @Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
        this.bmJdbcTemplate = bmJdbcTemplate;
    }

    /**
     * ECSタスク進捗の開始レコードを登録する。
     *
     * @param record 登録内容
     */
    public void insertStarted(EcsScrapeTaskProgressRecordEntity record) {
        String sql = """
            INSERT INTO ecs_scrape_task_progress (
                progress_id,
                batch_cd,
                task_id,
                task_arn,
                status,
                metadata,
                error_message,
                start_time,
                end_time,
                register_id,
                register_time,
                update_id,
                update_time
            ) VALUES (
                :progressId,
                :batchCd,
                :taskId,
                :taskArn,
                :status,
                CAST(:metadata AS jsonb),
                :errorMessage,
                :startTime,
                :endTime,
                'SYSTEM',
                CURRENT_TIMESTAMP,
                'SYSTEM',
                CURRENT_TIMESTAMP
            )
        """;

        this.bmJdbcTemplate.update(sql, toParams(record));
    }

    /**
     * ステータスとメタデータを更新する。
     *
     * @param progressId 進捗ID
     * @param status ステータス
     * @param metadata メタデータ(JSON文字列)
     * @param updateId 更新者
     * @return 更新件数
     */
    public int updateStatus(String progressId, String status, String metadata, String updateId) {
        String sql = """
            UPDATE ecs_scrape_task_progress
               SET status = :status,
                   metadata = CAST(:metadata AS jsonb),
                   update_id = :updateId,
                   update_time = CURRENT_TIMESTAMP
             WHERE progress_id = :progressId
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("progressId", progressId)
                .addValue("status", status)
                .addValue("metadata", metadata)
                .addValue("updateId", updateId);

        return this.bmJdbcTemplate.update(sql, params);
    }

    /**
     * ECS起動後に task_id / task_arn / status / metadata を更新する。
     *
     * @param progressId 進捗ID
     * @param taskId ECSタスクID
     * @param taskArn ECSタスクARN
     * @param status ステータス
     * @param metadata メタデータ(JSON文字列)
     * @param updateId 更新者
     * @return 更新件数
     */
    public int updateTaskInfo(
            String progressId,
            String taskId,
            String taskArn,
            String status,
            String metadata,
            String updateId) {

        String sql = """
            UPDATE ecs_scrape_task_progress
               SET task_id = :taskId,
                   task_arn = :taskArn,
                   status = :status,
                   metadata = CAST(:metadata AS jsonb),
                   update_id = 'SYSTEM',
                   update_time = CURRENT_TIMESTAMP
             WHERE progress_id = :progressId
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("progressId", progressId)
                .addValue("taskId", taskId)
                .addValue("taskArn", taskArn)
                .addValue("status", status)
                .addValue("metadata", metadata);

        return this.bmJdbcTemplate.update(sql, params);
    }

    /**
     * 正常終了／異常終了時に終了情報を更新する。
     *
     * @param progressId 進捗ID
     * @param status 終了ステータス
     * @param metadata メタデータ(JSON文字列)
     * @param errorMessage エラーメッセージ
     * @param updateId 更新者
     * @return 更新件数
     */
    public int updateFinished(
            String progressId,
            String status,
            String metadata,
            String errorMessage,
            String updateId) {

        String sql = """
            UPDATE ecs_scrape_task_progress
               SET status = :status,
                   metadata = CAST(:metadata AS jsonb),
                   error_message = :errorMessage,
                   end_time = CURRENT_TIMESTAMP,
                   update_id = 'SYSTEM',
                   update_time = CURRENT_TIMESTAMP
             WHERE progress_id = :progressId
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("progressId", progressId)
                .addValue("status", status)
                .addValue("metadata", metadata)
                .addValue("errorMessage", errorMessage);

        return this.bmJdbcTemplate.update(sql, params);
    }

    /**
     * 1レコード分のパラメータへ変換する。
     *
     * @param record エンティティ
     * @return SQLパラメータ
     */
    private MapSqlParameterSource toParams(EcsScrapeTaskProgressRecordEntity record) {
        return new MapSqlParameterSource()
                .addValue("progressId", record.getProgressId())
                .addValue("batchCd", record.getBatchCd())
                .addValue("taskId", record.getTaskId())
                .addValue("taskArn", record.getTaskArn())
                .addValue("status", record.getStatus())
                .addValue("metadata", record.getMetadata())
                .addValue("errorMessage", record.getErrorMessage())
                .addValue("startTime", toTimestamp(record.getStartTime()))
                .addValue("endTime", toTimestamp(record.getEndTime()));
    }

    /**
     * 同一バッチコードの未完了レコードのうち、
     * task_arn が未設定の最新 progress_id を取得する。
     *
     * @param batchCd バッチコード
     * @return progressId。該当なしの場合は null
     */
    public String findLatestOpenProgressIdWithoutTaskArn(String batchCd) {
        String sql = """
            SELECT progress_id
              FROM ecs_scrape_task_progress
             WHERE batch_cd = :batchCd
               AND status IN ('REQUESTED', 'RUNNING')
               AND (task_arn IS NULL OR BTRIM(task_arn) = '')
             ORDER BY start_time DESC, register_time DESC
             LIMIT 1
        """;

        List<String> list = this.bmJdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("batchCd", batchCd),
                (rs, rowNum) -> rs.getString("progress_id")
        );

        return list.isEmpty() ? null : list.get(0);
    }


    /**
     * LocalDateTime を Timestamp に変換する。
     *
     * @param value 変換対象
     * @return Timestamp
     */
    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
