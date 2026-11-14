package com.claims.platform.web;

import com.claims.platform.common.error.ErrorBody;
import com.claims.platform.common.error.ErrorCodes;
import com.claims.platform.common.error.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> api(ApiException ex, HttpServletRequest request) {
        return json(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return json(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, "Request validation failed", details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return json(HttpStatus.BAD_REQUEST, ErrorCodes.MALFORMED_JSON, "Request body is not valid JSON", List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> denied(AccessDeniedException ex, HttpServletRequest request) {
        return json(HttpStatus.FORBIDDEN, ErrorCodes.NOT_ALLOWED, "You are not allowed to perform this action", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> fallback(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error", ex);
        return json(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", List.of(), request);
    }

    private ResponseEntity<ErrorBody> json(HttpStatus status, String code, String message,
                                           List<ErrorDetail> details, HttpServletRequest request) {
        Object correlation = request.getAttribute(CorrelationIdFilter.ATTR);
        String correlationId = correlation == null ? null : correlation.toString();
        return ResponseEntity.status(status).body(new ErrorBody(code, message, details, correlationId));
    }
}
