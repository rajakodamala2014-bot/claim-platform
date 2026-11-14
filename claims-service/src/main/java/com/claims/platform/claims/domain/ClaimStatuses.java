package com.claims.platform.claims.domain;

public final class ClaimStatuses {
    public static final String SUBMITTED = "SUBMITTED";
    public static final String TRIAGED = "TRIAGED";
    public static final String IN_REVIEW = "IN_REVIEW";
    public static final String AWAITING_INFO = "AWAITING_INFO";
    public static final String ASSESSED = "ASSESSED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String SETTLED = "SETTLED";
    public static final String CLOSED = "CLOSED";

    private ClaimStatuses() {
    }

    public static String label(String status) {
        return switch (status) {
            case SUBMITTED -> "Claim received";
            case TRIAGED -> "Claim queued for review";
            case IN_REVIEW -> "An adjuster is reviewing";
            case AWAITING_INFO -> "More information is needed";
            case ASSESSED -> "Assessment completed";
            case APPROVED -> "Claim approved";
            case REJECTED -> "Claim rejected";
            case SETTLED -> "Payment completed";
            case CLOSED -> "Claim closed";
            default -> status;
        };
    }
}
