package com.claims.platform.web.problem;

import com.claims.platform.web.correlation.CorrelationIdFilter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

/**
 * RFC 9457 problem details with the platform's extension members:
 * <ul>
 *     <li>{@code code}: stable machine-readable error code (clients switch on this, never on {@code title})</li>
 *     <li>{@code correlationId}: same id as in logs and events</li>
 *     <li>{@code errors}: field-level violations for VALIDATION_FAILED</li>
 * </ul>
 */
public final class Problems {
    public static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;
    private static final String TYPE_PREFIX = "https://errors.claims.example/";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase().replace('_', '-')));
        problem.setTitle(titleOf(code));
        problem.setProperty("code", code);
        CorrelationIdFilter.current().ifPresent(id -> problem.setProperty("correlationId", id));
        return problem;
    }

    public static ProblemDetail withErrors(ProblemDetail problem, List<FieldError> errors) {
        problem.setProperty("errors", errors);
        return problem;
    }

    public static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(PROBLEM_JSON).body(problem);
    }

    /** {@code CLAIM_NOT_FOUND} -> {@code Claim not found}. */
    static String titleOf(String code) {
        String words = code.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    public record FieldError(String field, String message) {
    }
}
