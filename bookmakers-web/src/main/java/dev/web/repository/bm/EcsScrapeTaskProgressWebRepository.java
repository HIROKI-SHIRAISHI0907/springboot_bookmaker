package dev.web.repository.bm;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.bm_a009.EcsScrapeTaskProgressRecordEntity;
import dev.web.com.OpenProgressRecord;


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
    	debugConnection();
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
    	debugConnection();
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
    	debugConnection();

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
    	debugConnection();

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
     * 通常実行時のレコードを取得
     * @param batchCd
     * @return
     */
    public OpenProgressRecord findLatestOpenRecord(String batchCd) {
        String sql = """
            SELECT progress_id, batch_cd, task_id, task_arn, status
              FROM ecs_scrape_task_progress
             WHERE batch_cd = :batchCd
               AND status IN ('REQUESTED', 'PENDING', 'PROVISIONING', 'ACTIVATING', 'RUNNING')
             ORDER BY register_time DESC
             LIMIT 1
        """;

        List<OpenProgressRecord> list = this.bmJdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("batchCd", batchCd),
                (rs, rowNum) -> {
                    OpenProgressRecord r = new OpenProgressRecord();
                    r.setProgressId(rs.getString("progress_id"));
                    r.setBatchCd(rs.getString("batch_cd"));
                    r.setTaskId(rs.getString("task_id"));
                    r.setTaskArn(rs.getString("task_arn"));
                    r.setStatus(rs.getString("status"));
                    return r;
                }
        );

        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 最新のバッチコードレコードを取得
     * @param batchCd
     * @return
     */
    public EcsScrapeTaskProgressRecordEntity findLatestByBatchCd(String batchCd) {
        String sql = """
            SELECT progress_id, batch_cd, task_id, task_arn, status, metadata, error_message,
                   start_time, end_time, register_id, register_time, update_id, update_time
              FROM ecs_scrape_task_progress
             WHERE batch_cd = :batchCd
             ORDER BY register_time DESC
             LIMIT 1
        """;

        List<EcsScrapeTaskProgressRecordEntity> list = this.bmJdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("batchCd", batchCd),
                (rs, rowNum) -> {
                    EcsScrapeTaskProgressRecordEntity e = new EcsScrapeTaskProgressRecordEntity();
                    e.setProgressId(rs.getString("progress_id"));
                    e.setBatchCd(rs.getString("batch_cd"));
                    e.setTaskId(rs.getString("task_id"));
                    e.setTaskArn(rs.getString("task_arn"));
                    e.setStatus(rs.getString("status"));
                    e.setMetadata(rs.getString("metadata"));
                    e.setErrorMessage(rs.getString("error_message"));
                    return e;
                }
        );

        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 接続先DB情報をデバッグ出力する。
     */
    public void debugConnection() {
        String sql = """
            SELECT
                current_database() AS current_database,
                current_schema()   AS current_schema,
                current_user       AS current_user
        """;

        Map<String, Object> result =
                this.bmJdbcTemplate.queryForMap(sql, new MapSqlParameterSource());

        System.out.println("[DEBUG] ecs_scrape_task_progress connection = " + result);
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
