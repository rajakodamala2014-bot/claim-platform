package com.claims.platform.insights.exception;

import com.claims.platform.web.exception.PlatformApiErrors;
import com.claims.platform.web.exception.Problems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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
        return Problems.response(PlatformApiErrors.typeMismatch(ex.getName()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return Problems.response(PlatformApiErrors.internalError());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return PlatformApiErrors.response(PlatformApiErrors.validationFailed(PlatformApiErrors.fieldErrors(ex)));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        return PlatformApiErrors.response(PlatformApiErrors.validationFailed(PlatformApiErrors.fieldErrors(ex)));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return PlatformApiErrors.response(PlatformApiErrors.unreadableBody());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        return PlatformApiErrors.frameworkStatus(body, statusCode);
    }
}
