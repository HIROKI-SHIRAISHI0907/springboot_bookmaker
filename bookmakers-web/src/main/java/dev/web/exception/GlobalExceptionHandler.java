package dev.web.exception;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.common.util.DateUtil;
import jakarta.servlet.http.HttpServletRequest;

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
	private static final DateTimeFormatter JAPANESE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
