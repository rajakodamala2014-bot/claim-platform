package com.claims.platform.claims.platform.exception;

import com.claims.platform.claims.core.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** HTTP status for every stable error code. Exhaustive switch: a new code won't compile until it's mapped. */
public final class ErrorCodeHttpStatusMapper {
    private ErrorCodeHttpStatusMapper() {
    }

    public static HttpStatus toHttpStatus(ErrorCode code) {
        return switch (code) {
            case VALIDATION_FAILED, IDEMPOTENCY_KEY_REQUIRED -> HttpStatus.BAD_REQUEST;
            case CLAIM_NOT_FOUND, DECISION_NOT_FOUND, INFO_REQUEST_NOT_FOUND, SETTLEMENT_NOT_FOUND, UPLOAD_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
            case CLAIM_INVALID_TRANSITION, CLAIM_ALREADY_ASSIGNED, CONCURRENT_UPDATE, SETTLEMENT_NOT_RETRYABLE ->
                    HttpStatus.CONFLICT;
            case STALE_VERSION -> HttpStatus.PRECONDITION_FAILED;
            case POLICY_NOT_IN_FORCE, POLICY_NOT_FOUND, SETTLEMENT_EXCEEDS_APPROVED, CURRENCY_MISMATCH,
                 IDEMPOTENCY_KEY_REUSED, UPLOAD_REJECTED, MARKET_NOT_SUPPORTED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case AMOUNT_EXCEEDS_AUTHORITY, FORBIDDEN_MARKET, NOT_CLAIM_OWNER, NOT_ASSIGNED_HANDLER, FORBIDDEN_ROLE,
                 SELF_APPROVAL_NOT_ALLOWED -> HttpStatus.FORBIDDEN;
            case POLICY_SYSTEM_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case IF_MATCH_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
            case INVALID_SIGNATURE, UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
        };
    }
}
