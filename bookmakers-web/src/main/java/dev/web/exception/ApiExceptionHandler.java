package dev.web.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJsonParse(HttpMessageNotReadableException e) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", "JSON形式または文字コードが不正です。UTF-8 の application/json を送ってください。");
        body.put("detail", e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
