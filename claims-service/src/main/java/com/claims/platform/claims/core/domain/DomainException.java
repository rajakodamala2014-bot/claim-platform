package com.claims.platform.claims.core.domain;

import java.util.Objects;

/** A business rule was violated. Mapped to problem+json by the single global exception handler. */
public class DomainException extends RuntimeException {
    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ErrorCode code() {
        return code;
    }
}
