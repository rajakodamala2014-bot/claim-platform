package com.claims.platform.common.error;

public final class ErrorCodes {
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String MALFORMED_JSON = "MALFORMED_JSON";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String NOT_ALLOWED = "NOT_ALLOWED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String ALREADY_ASSIGNED = "ALREADY_ASSIGNED";
    public static final String VERSION_MISMATCH = "VERSION_MISMATCH";
    public static final String INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";
    public static final String POLICY_NOT_ACTIVE = "POLICY_NOT_ACTIVE";
    public static final String INCIDENT_OUTSIDE_COVERAGE = "INCIDENT_OUTSIDE_COVERAGE";
    public static final String PERIL_NOT_COVERED = "PERIL_NOT_COVERED";
    public static final String LINE_OF_BUSINESS_MISMATCH = "LINE_OF_BUSINESS_MISMATCH";
    public static final String CURRENCY_MISMATCH = "CURRENCY_MISMATCH";
    public static final String APPROVAL_AUTHORITY_EXCEEDED = "APPROVAL_AUTHORITY_EXCEEDED";
    public static final String IDEMPOTENCY_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED";
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String POLICY_SERVICE_UNAVAILABLE = "POLICY_SERVICE_UNAVAILABLE";
    public static final String CLAIMS_SERVICE_UNAVAILABLE = "CLAIMS_SERVICE_UNAVAILABLE";

    private ErrorCodes() {
    }
}
