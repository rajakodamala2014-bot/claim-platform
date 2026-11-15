package com.claims.platform.claims.core.domain;

/**
 * Stable error codes surfaced as the {@code code} member of problem+json responses. Clients may switch on
 * these, so never rename one. The HTTP status for each is decided in the API layer's global handler.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    CLAIM_NOT_FOUND,
    CLAIM_INVALID_TRANSITION,
    CLAIM_ALREADY_ASSIGNED,
    STALE_VERSION,
    POLICY_NOT_IN_FORCE,
    AMOUNT_EXCEEDS_AUTHORITY,
    SETTLEMENT_EXCEEDS_APPROVED,
    FORBIDDEN_MARKET,
    NOT_CLAIM_OWNER,
    // beyond the minimum list, needed by the richer flow
    NOT_ASSIGNED_HANDLER,
    FORBIDDEN_ROLE,
    SELF_APPROVAL_NOT_ALLOWED,
    CURRENCY_MISMATCH,
    DECISION_NOT_FOUND,
    INFO_REQUEST_NOT_FOUND,
    SETTLEMENT_NOT_FOUND,
    SETTLEMENT_NOT_RETRYABLE,
    /** Another request changed the claim between our read and write (no If-Match was sent). */
    CONCURRENT_UPDATE,
    POLICY_NOT_FOUND,
    POLICY_SYSTEM_UNAVAILABLE,
    MARKET_NOT_SUPPORTED,
    IDEMPOTENCY_KEY_REQUIRED,
    IDEMPOTENCY_KEY_REUSED,
    UPLOAD_NOT_FOUND,
    UPLOAD_REJECTED,
    /** Staff state changes must send If-Match with the claim version (ETag). */
    IF_MATCH_REQUIRED,
    /** Webhook/callback signature missing or wrong. */
    INVALID_SIGNATURE
}
