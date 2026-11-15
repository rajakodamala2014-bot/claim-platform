package com.claims.platform.common.event;

import java.util.Set;

/** {@link EventEnvelope#type()} values. Claim events go on claims.claim-events; SLA alerts on insights.alerts. */
public final class EventTypes {
    // intake
    public static final String CLAIM_SUBMITTED = "ClaimSubmitted";
    public static final String CLAIM_TRIAGED = "ClaimTriaged";
    public static final String COVER_DECLINED = "CoverDeclined";
    // handling
    public static final String CLAIM_ASSIGNED = "ClaimAssigned";
    public static final String ASSESSMENT_STARTED = "AssessmentStarted";
    public static final String RESERVE_CHANGED = "ReserveChanged";
    public static final String INFORMATION_REQUESTED = "InformationRequested";
    public static final String INFORMATION_PROVIDED = "InformationProvided";
    public static final String INFORMATION_REQUEST_EXPIRED = "InformationRequestExpired";
    public static final String ASSESSMENT_RECORDED = "AssessmentRecorded";
    public static final String DECISION_PROPOSED = "DecisionProposed";
    public static final String DECISION_APPROVED = "DecisionApproved";
    public static final String DECISION_RETURNED = "DecisionReturned";
    public static final String CLAIM_APPROVED = "ClaimApproved";
    public static final String CLAIM_REJECTED = "ClaimRejected";
    // settlement
    public static final String SETTLEMENT_INSTRUCTED = "SettlementInstructed";
    public static final String PAYMENT_CONFIRMED = "PaymentConfirmed";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String CLAIM_SETTLED = "ClaimSettled";
    // lifecycle
    public static final String CLAIM_WITHDRAWN = "ClaimWithdrawn";
    public static final String CLAIM_REOPENED = "ClaimReopened";
    public static final String CLAIM_CLOSED = "ClaimClosed";

    // insights.alerts
    public static final String SLA_AT_RISK = "SlaAtRisk";
    public static final String SLA_BREACHED = "SlaBreached";

    public static final Set<String> CLAIM_EVENTS = Set.of(
            CLAIM_SUBMITTED, CLAIM_TRIAGED, COVER_DECLINED, CLAIM_ASSIGNED, ASSESSMENT_STARTED, RESERVE_CHANGED,
            INFORMATION_REQUESTED, INFORMATION_PROVIDED, INFORMATION_REQUEST_EXPIRED, ASSESSMENT_RECORDED,
            DECISION_PROPOSED, DECISION_APPROVED, DECISION_RETURNED, CLAIM_APPROVED, CLAIM_REJECTED,
            SETTLEMENT_INSTRUCTED, PAYMENT_CONFIRMED, PAYMENT_FAILED, CLAIM_SETTLED, CLAIM_WITHDRAWN,
            CLAIM_REOPENED, CLAIM_CLOSED);

    public static final Set<String> ALERTS = Set.of(SLA_AT_RISK, SLA_BREACHED);

    private EventTypes() {
    }
}
