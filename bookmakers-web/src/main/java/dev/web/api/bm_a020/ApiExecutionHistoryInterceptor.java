package dev.web.api.bm_a020;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiExecutionHistoryInterceptor implements HandlerInterceptor {

    private static final String ATTR_EXECUTION_ID = "executionHistory.executionId";
    private static final String ATTR_START_TIME = "executionHistory.startTime";

    private final WebExecutionHistoryService executionHistoryService;

    public ApiExecutionHistoryInterceptor(WebExecutionHistoryService executionHistoryService) {
        this.executionHistoryService = executionHistoryService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        LocalDateTime startTime = LocalDateTime.now();

        String requestedBy = resolveRequestedBy(request);
        String traceId = resolveTraceId(request);

        String executionId = executionHistoryService.startApiExecution(
            handlerMethod.getBeanType().getSimpleName() + "." + handlerMethod.getMethod().getName(),
            request.getRequestURI(),
            request.getMethod(),
            handlerMethod.getBeanType().getName(),
            handlerMethod.getMethod().getName(),
            requestedBy,
            traceId,
            startTime
        );

        request.setAttribute(ATTR_EXECUTION_ID, executionId);
        request.setAttribute(ATTR_START_TIME, startTime);

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        Object executionIdObj = request.getAttribute(ATTR_EXECUTION_ID);
        Object startTimeObj = request.getAttribute(ATTR_START_TIME);

        if (executionIdObj == null || startTimeObj == null) {
            return;
        }

        String executionId = (String) executionIdObj;
        LocalDateTime startTime = (LocalDateTime) startTimeObj;
        LocalDateTime endTime = LocalDateTime.now();

        if (ex == null && response.getStatus() < 500) {
            executionHistoryService.finishSuccess(executionId, startTime, endTime);
        } else {
            Throwable throwable = ex != null ? ex : new RuntimeException("HTTP status=" + response.getStatus());
            executionHistoryService.finishFailure(executionId, startTime, endTime, throwable);
        }
    }

    private String resolveRequestedBy(HttpServletRequest request) {
        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }
        return "anonymous";
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return (traceId == null || traceId.isBlank()) ? null : traceId;
    }
}
