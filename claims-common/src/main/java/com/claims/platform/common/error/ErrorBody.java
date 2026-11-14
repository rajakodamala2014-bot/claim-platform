package com.claims.platform.common.error;

import java.util.List;

public record ErrorBody(Error error) {
    public ErrorBody(String code, String message, List<ErrorDetail> details, String correlationId) {
        this(new Error(code, message, details == null ? List.of() : details, correlationId));
    }

    public record Error(String code, String message, List<ErrorDetail> details, String correlationId) {
    }
}
