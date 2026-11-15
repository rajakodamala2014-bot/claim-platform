package com.claims.platform.insights.exception;

/** Read-side rule violations, mapped to problem+json by the API layer. */
public class InsightsException extends RuntimeException {
    public enum Code { VALIDATION_FAILED, FORBIDDEN_MARKET, FORBIDDEN_ROLE }

    private final Code code;

    public InsightsException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
