package dev.batch.common;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import dev.batch.repository.bm.ExecutionHistoryBatchRepository;
import dev.common.entity.ExecutionHistoryRecordEntity;

/**
 * 実行履歴を管理するサービス。
 *
 * <p>
 * API・バッチ共通で使用する。
 * 開始登録と RUNNING 更新は同期、
 * SUCCESS / FAILED 更新は非同期で行う。
 * </p>
 */
@Service
public class BatchExecutionHistoryService {

    @Autowired
    private ExecutionHistoryBatchRepository executionHistoryRepository;

    /**
     * バッチ開始履歴を登録する。
     *
     * @param executionId 実行ID
     * @param executionName 実行名
     * @param jobName ジョブ名
     * @param className クラス名
     * @param methodName メソッド名
     * @param startTime 開始日時
     */
    public void registerBatchStart(
            String executionId,
            String executionName,
            String jobName,
            String className,
            String methodName,
            LocalDateTime startTime) {

        ExecutionHistoryRecordEntity entity = new ExecutionHistoryRecordEntity();
        entity.setExecutionId(executionId);
        entity.setExecutionType("BATCH");
        entity.setExecutionName(executionName);
        entity.setStatus("STARTED");
        entity.setTriggerType("BATCH");
        entity.setClassName(className);
        entity.setMethodName(methodName);
        entity.setJobName(jobName);
        entity.setRequestedBy("system");
        entity.setStartTime(startTime);

        executionHistoryRepository.insertStart(entity);
    }

    /**
     * バッチを RUNNING 状態に更新する。
     *
     * @param executionId 実行ID
     */
    public void markBatchRunning(String executionId) {
        executionHistoryRepository.updateStatus(executionId, "RUNNING");
    }

    /**
     * バッチ正常終了を記録する。
     *
     * @param executionId 実行ID
     * @param startTime 開始日時
     * @param endTime 終了日時
     */
    @Async("executionHistoryExecutor")
    public void markBatchSuccess(
            String executionId,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        long durationMs = Duration.between(startTime, endTime).toMillis();

        executionHistoryRepository.updateSuccess(
            executionId,
            "SUCCESS",
            endTime,
            durationMs
        );
    }

    /**
     * バッチ異常終了を記録する。
     *
     * @param executionId 実行ID
     * @param startTime 開始日時
     * @param endTime 終了日時
     * @param throwable 例外
     */
    @Async("executionHistoryExecutor")
    public void markBatchFailure(
            String executionId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Throwable throwable) {

        long durationMs = Duration.between(startTime, endTime).toMillis();

        executionHistoryRepository.updateFailure(
            executionId,
            "FAILED",
            endTime,
            durationMs,
            buildErrorMessage(throwable)
        );
    }

    private String buildErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        String message = throwable.getMessage();
        String value;

        if (message == null || message.isBlank()) {
            value = throwable.getClass().getName();
        } else {
            value = throwable.getClass().getName() + ": " + message;
        }

        return value.length() > 4000 ? value.substring(0, 4000) : value;
    }
}
