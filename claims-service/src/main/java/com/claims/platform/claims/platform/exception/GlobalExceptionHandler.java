package com.claims.platform.claims.platform.exception;

import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.exception.ValidationException;
import com.claims.platform.common.web.Headers;
import com.claims.platform.web.exception.Problems;
import com.claims.platform.web.exception.Problems.FieldError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * THE global exception handler: every error leaves the service as RFC 9457 problem+json with a stable
 * {@code code}. Domain exceptions carry their code; framework exceptions are mapped here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> domain(DomainException ex) {
        ProblemDetail problem = Problems.of(ErrorCodeHttpStatusMapper.toHttpStatus(ex.code()), ex.code().name(), ex.getMessage());
        if (ex instanceof ValidationException validation) {
            Problems.withErrors(problem, validation.violations().stream()
                    .map(v -> new FieldError(v.field(), v.message())).toList());
        }
        return Problems.response(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return Problems.response(validation(List.of(new FieldError(ex.getName(), "has an invalid format"))));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return Problems.response(Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong. Quote the correlation id if you contact support."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage())).toList();
        return problem(validation(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldError> errors = ex.getAllValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldError(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return problem(validation(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return problem(Problems.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(),
                "Request body is missing or is not valid JSON"));
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
                                                                          HttpHeaders headers, HttpStatusCode status,
                                                                          WebRequest request) {
        if (ex instanceof MissingRequestHeaderException missing && Headers.IDEMPOTENCY_KEY.equals(missing.getHeaderName())) {
            return problem(Problems.of(HttpStatus.BAD_REQUEST, ErrorCode.IDEMPOTENCY_KEY_REQUIRED.name(),
                    "Idempotency-Key header (a UUID) is required"));
        }
        return problem(Problems.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(), ex.getMessage()));
    }

    /** Any other framework exception (404 no route, 405, 415...) still gets a code. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String detail = body instanceof ProblemDetail pd && pd.getDetail() != null ? pd.getDetail() : status.getReasonPhrase();
        return problem(Problems.of(status, status.name(), detail));
    }

    private static ProblemDetail validation(List<FieldError> errors) {
        return Problems.withErrors(Problems.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(),
                "Request validation failed"), errors);
    }

    private static ResponseEntity<Object> problem(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(Problems.PROBLEM_JSON).body(problem);
    }
}
