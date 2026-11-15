package com.claims.platform.common.web;

/** HTTP / message header names shared by every service. */
public final class Headers {
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String INTERNAL_TOKEN = "X-Internal-Token";

    /** MDC key under which the correlation id is stored for logging. */
    public static final String CORRELATION_ID_MDC = "correlationId";

    private Headers() {
    }
}
