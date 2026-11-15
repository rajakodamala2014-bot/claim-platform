package com.claims.platform.notification.application;

/** Rule violations, mapped to problem+json by the API layer. */
public class NotificationException extends RuntimeException {
    public enum Code { VALIDATION_FAILED, NOT_FOUND, FORBIDDEN_MARKET, INVALID_SIGNATURE }

    private final Code code;

    public NotificationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
