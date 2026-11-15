package com.claims.platform.web.exception;

import com.claims.platform.web.exception.Problems.FieldError;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;

/**
 * The error contract shared by every service: problem+json, a stable {@code code}, and field errors
 * for a body or parameter the caller can fix.
 */
public final class PlatformApiErrors {
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String UNREADABLE_BODY = "Request body is missing or is not valid JSON";
    public static final String INTERNAL_DETAIL = "Something went wrong. Quote the correlation id if you contact support.";

    private PlatformApiErrors() {
    }

    public static ProblemDetail validationFailed(List<FieldError> errors) {
        return Problems.withErrors(Problems.of(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Request validation failed"), errors);
    }

    public static ProblemDetail typeMismatch(String field) {
        return validationFailed(List.of(new FieldError(field, "has an invalid format")));
    }

    public static ProblemDetail unreadableBody() {
        return Problems.of(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, UNREADABLE_BODY);
    }

    public static ProblemDetail internalError() {
        return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, INTERNAL_DETAIL);
    }

    public static List<FieldError> fieldErrors(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
    }

    public static List<FieldError> fieldErrors(HandlerMethodValidationException ex) {
        return ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
    }

    public static ResponseEntity<Object> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(Problems.PROBLEM_JSON).body(problem);
    }

    /** Fallback for framework statuses that no more specific handler claimed. */
    public static ResponseEntity<Object> frameworkStatus(Object body, HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String detail = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : status.getReasonPhrase();
        return response(Problems.of(status, status.name(), detail));
    }
}
