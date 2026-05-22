package dev.web.repository.bm;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.entity.ExecutionHistoryRecordEntity;

@Repository
public class ExecutionHistoryWebRepository {

    private final NamedParameterJdbcTemplate bmJdbcTemplate;

    public ExecutionHistoryWebRepository(NamedParameterJdbcTemplate bmJdbcTemplate) {
        this.bmJdbcTemplate = bmJdbcTemplate;
    }

    public void insertStarted(ExecutionHistoryRecordEntity record) {
        String sql = """
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
                :executionId,
                :executionType,
                :executionName,
                :status,
                :triggerType,
                :requestPath,
                :httpMethod,
                :className,
                :methodName,
                :jobName,
                :requestedBy,
                :traceId,
                :startTime,
                :endTime,
                :durationMs,
                :errorMessage,
                'SYSTEM',
                CURRENT_TIMESTAMP,
                'SYSTEM',
                CURRENT_TIMESTAMP
            )
        """;

        bmJdbcTemplate.update(sql, toParams(record));
    }

    public void updateFinished(ExecutionHistoryRecordEntity record) {
        String sql = """
            UPDATE execution_history
               SET status = :status,
                   end_time = :endTime,
                   duration_ms = :durationMs,
                   error_message = :errorMessage,
                   update_id = 'SYSTEM',
        		   update_time = CURRENT_TIMESTAMP
             WHERE execution_id = :executionId
        """;

        bmJdbcTemplate.update(
            sql,
            new MapSqlParameterSource()
                .addValue("executionId", record.getExecutionId())
                .addValue("status", record.getStatus())
                .addValue("endTime", record.getEndTime())
                .addValue("durationMs", record.getDurationMs())
                .addValue("errorMessage", record.getErrorMessage())
        );
    }

    private MapSqlParameterSource toParams(ExecutionHistoryRecordEntity record) {
        return new MapSqlParameterSource()
            .addValue("executionId", record.getExecutionId())
            .addValue("executionType", record.getExecutionType())
            .addValue("executionName", record.getExecutionName())
            .addValue("status", record.getStatus())
            .addValue("triggerType", record.getTriggerType())
            .addValue("requestPath", record.getRequestPath())
            .addValue("httpMethod", record.getHttpMethod())
            .addValue("className", record.getClassName())
            .addValue("methodName", record.getMethodName())
            .addValue("jobName", record.getJobName())
            .addValue("requestedBy", record.getRequestedBy())
            .addValue("traceId", record.getTraceId())
            .addValue("startTime", record.getStartTime())
            .addValue("endTime", record.getEndTime())
            .addValue("durationMs", record.getDurationMs())
            .addValue("errorMessage", record.getErrorMessage());
    }
}
