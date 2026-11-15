package com.claims.platform.claims.core.exception;

import java.util.List;

/** VALIDATION_FAILED with one entry per offending field, so clients can highlight form fields. */
public class ValidationException extends DomainException {
    private final List<FieldViolation> violations;

    public ValidationException(List<FieldViolation> violations) {
        super(ErrorCode.VALIDATION_FAILED, "Request validation failed: " + violations.stream()
                .map(v -> v.field() + " " + v.message()).reduce((a, b) -> a + "; " + b).orElse(""));
        this.violations = List.copyOf(violations);
    }

    public static ValidationException of(String field, String message) {
        return new ValidationException(List.of(new FieldViolation(field, message)));
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    public record FieldViolation(String field, String message) {
    }
}
