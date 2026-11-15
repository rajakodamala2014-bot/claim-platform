package com.claims.platform.insights.exception;

import com.claims.platform.insights.exception.InsightsException;
import com.claims.platform.web.exception.Problems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** The service's single problem+json handler (same envelope and codes as Claims Service). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InsightsException.class)
    ResponseEntity<ProblemDetail> insights(InsightsException ex) {
        HttpStatus status = switch (ex.code()) {
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN_MARKET, FORBIDDEN_ROLE -> HttpStatus.FORBIDDEN;
        };
        return Problems.response(Problems.of(status, ex.code().name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return Problems.response(Problems.withErrors(Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed"), java.util.List.of(new Problems.FieldError(ex.getName(), "has an invalid format"))));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return Problems.response(Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong"));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ProblemDetail problem = Problems.of(status, status.name(), status.getReasonPhrase());
        return ResponseEntity.status(status).contentType(Problems.PROBLEM_JSON).body(problem);
    }
}
