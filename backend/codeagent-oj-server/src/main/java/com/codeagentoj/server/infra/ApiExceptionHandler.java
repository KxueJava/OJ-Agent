package com.codeagentoj.server.infra;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleCredentials(BadCredentialsException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
        detail.setTitle("Authentication failed");
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst().map(error -> error.getField() + " " + error.getDefaultMessage()).orElse("请求参数无效");
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        detail.setTitle("Invalid request");
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setTitle("Invalid request");
        detail.setProperty("timestamp", Instant.now());
        detail.setProperty("errors", Map.of());
        return detail;
    }

    /**
     * ResponseStatusException（403/404/409 等）之前没有处理器，响应体里没有 detail 字段，
     * 前端只能显示"请求失败（HTTP 409）"，看不到"状态为 CANCELLED 不能发布"这类真实原因。
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ProblemDetail handleStatus(org.springframework.web.server.ResponseStatusException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatusCode(),
                exception.getReason() == null ? exception.getStatusCode().toString() : exception.getReason());
        detail.setTitle("Request rejected");
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }
}
