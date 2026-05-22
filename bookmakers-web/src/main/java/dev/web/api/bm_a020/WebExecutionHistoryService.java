package dev.web.api.bm_a020;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import dev.common.entity.ExecutionHistoryRecordEntity;
import dev.web.repository.bm.ExecutionHistoryWebRepository;

@Service
public class WebExecutionHistoryService {

    private final ExecutionHistoryWebRepository executionHistoryRepository;

    public WebExecutionHistoryService(ExecutionHistoryWebRepository executionHistoryRepository) {
        this.executionHistoryRepository = executionHistoryRepository;
    }

    public String startApiExecution(
            String executionName,
            String requestPath,
            String httpMethod,
            String className,
            String methodName,
            String requestedBy,
            String traceId,
            LocalDateTime startTime) {

        String executionId = UUID.randomUUID().toString();

        ExecutionHistoryRecordEntity record = new ExecutionHistoryRecordEntity();
        record.setExecutionId(executionId);
        record.setExecutionType("API");
        record.setExecutionName(executionName);
        record.setStatus("STARTED");
        record.setTriggerType("HTTP");
        record.setRequestPath(requestPath);
        record.setHttpMethod(httpMethod);
        record.setClassName(className);
        record.setMethodName(methodName);
        record.setRequestedBy(requestedBy);
        record.setTraceId(traceId);
        record.setStartTime(startTime);

        executionHistoryRepository.insertStarted(record);
        return executionId;
    }

    public String startBatchExecution(
            String executionName,
            String jobName,
            String className,
            String methodName,
            String requestedBy,
            String traceId,
            String triggerType,
            LocalDateTime startTime) {

        String executionId = UUID.randomUUID().toString();

        ExecutionHistoryRecordEntity record = new ExecutionHistoryRecordEntity();
        record.setExecutionId(executionId);
        record.setExecutionType("BATCH");
        record.setExecutionName(executionName);
        record.setStatus("STARTED");
        record.setTriggerType(triggerType);
        record.setJobName(jobName);
        record.setClassName(className);
        record.setMethodName(methodName);
        record.setRequestedBy(requestedBy);
        record.setTraceId(traceId);
        record.setStartTime(startTime);

        executionHistoryRepository.insertStarted(record);
        return executionId;
    }

    @Async("executionHistoryExecutor")
    public void finishSuccess(String executionId, LocalDateTime startTime, LocalDateTime endTime) {
    	ExecutionHistoryRecordEntity record = new ExecutionHistoryRecordEntity();
        record.setExecutionId(executionId);
        record.setStatus("SUCCESS");
        record.setEndTime(endTime);
        record.setDurationMs(Duration.between(startTime, endTime).toMillis());
        executionHistoryRepository.updateFinished(record);
    }

    @Async("executionHistoryExecutor")
    public void finishFailure(String executionId, LocalDateTime startTime, LocalDateTime endTime, Throwable throwable) {
    	ExecutionHistoryRecordEntity record = new ExecutionHistoryRecordEntity();
        record.setExecutionId(executionId);
        record.setStatus("FAILED");
        record.setEndTime(endTime);
        record.setDurationMs(Duration.between(startTime, endTime).toMillis());
        record.setErrorMessage(buildErrorMessage(throwable));
        executionHistoryRepository.updateFinished(record);
    }

    private String buildErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getName();
        }

        String value = throwable.getClass().getName() + ": " + message;
        return value.length() > 4000 ? value.substring(0, 4000) : value;
    }
}
