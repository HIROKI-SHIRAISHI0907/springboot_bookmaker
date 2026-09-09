package dev.web.exception;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.amazonaws.SdkClientException;

import dev.common.util.DateUtil;
import jakarta.servlet.http.HttpServletRequest;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

/**
 * 例外ハンドラークラス
 * @author shiraishitoshio
 *
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 日本形式（MySQL DATETIME 対応） → 例: "2025-07-22 19:30:00"
     */
    private static final DateTimeFormatter JAPANESE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AwsCostException.class)
    public ResponseEntity<ApiErrorResponse> handleAwsCostException(AwsCostException e) {
        log.warn("AwsCostException: {}", e.getMessage());
        return ResponseEntity.status(e.getHttpStatus()).body(new ApiErrorResponse(e.getMessage()));
    }

    /**
     * ResponseStatusException はステータスを尊重して返す
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {
        ex.printStackTrace();

        int statusCode = ex.getStatusCode().value();
        String errorText = HttpStatus.valueOf(statusCode).getReasonPhrase();
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();

        ApiError error = new ApiError(
                LocalDateTime.parse(DateUtil.getSysDate(), JAPANESE_FORMAT),
                statusCode,
                errorText,
                message,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(statusCode)
                .body(error);
    }

    @ExceptionHandler(AwsServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleAwsServiceException(AwsServiceException e) {
        // 認証エラー(InvalidClientTokenId, AccessDenied等)はここに来る。
        log.warn("AWS API error: {}", e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage());
        String message = "AWSからエラーが返却されました。認証情報または権限(ce:GetCostAndUsage等)を確認してください。";
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiErrorResponse(message));
    }

    @ExceptionHandler(SdkClientException.class)
    public ResponseEntity<ApiErrorResponse> handleSdkClientException(SdkClientException e) {
        log.warn("AWS SDK client error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("AWSへの接続に失敗しました。ネットワークまたは認証情報を確認してください。"));
    }

    /**
     * 想定外の例外（全ての例外の最後の砦）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(
            Exception ex,
            HttpServletRequest request) {
        ex.printStackTrace();

        ApiError error = new ApiError(
                LocalDateTime.parse(DateUtil.getSysDate(), JAPANESE_FORMAT),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
}
