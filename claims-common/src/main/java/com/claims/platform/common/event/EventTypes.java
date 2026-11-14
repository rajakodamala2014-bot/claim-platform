package com.claims.platform.common.event;

public final class EventTypes {
    public static final String CLAIM_SUBMITTED = "ClaimSubmitted";
    public static final String CLAIM_TRIAGED = "ClaimTriaged";
    public static final String CLAIM_ASSIGNED = "ClaimAssigned";
    public static final String INFO_REQUESTED = "InfoRequested";
    public static final String INFO_PROVIDED = "InfoProvided";
    public static final String CLAIM_ASSESSED = "ClaimAssessed";
    public static final String CLAIM_APPROVED = "ClaimApproved";
    public static final String CLAIM_REJECTED = "ClaimRejected";
    public static final String SETTLEMENT_PAID = "SettlementPaid";
    public static final String CLAIM_CLOSED = "ClaimClosed";
    public static final String CLAIM_REOPENED = "ClaimReopened";
    public static final String DOCUMENT_UPLOADED = "DocumentUploaded";

    private EventTypes() {
    }
}
