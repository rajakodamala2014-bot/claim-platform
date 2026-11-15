package com.claims.platform.insights.entity;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-model row for one claim, built only from claims.claim-events. {@link #apply} folds one event in; it
 * never fails on events it doesn't care about (forward compatible). Claims Service stays the source of truth:
 * this is a denormalised copy that may lag a few seconds.
 */
public final class ClaimSummary {
    private final UUID claimId;
    private final String claimNumber;
    private final String market;
    private final String lineOfBusiness;
    private final String policyNumber;
    private final String claimantId;
    private final String channel;
    private final Instant submittedAt;
    private String status;
    private String coverStatus;
    private String handlerId;
    private String teamId;
    private Instant slaDueAt;
    private boolean slaPaused;
    private Instant decidedAt;
    private Instant settledAt;
    private Instant closedAt;
    private long reserveOutstandingMinor;
    private String currency;
    private PendingDecision pendingDecision;
    private int reopenCount;
    private Instant lastEventAt;

    /** A decision waiting in a team lead's approvals inbox. */
    public record PendingDecision(UUID decisionId, String outcome, long amountMinor, String proposedBy, Instant proposedAt) {
    }

    public ClaimSummary(UUID claimId, String claimNumber, String market, String lineOfBusiness, String policyNumber,
                        String claimantId, String channel, Instant submittedAt, String status, String coverStatus,
                        String handlerId, String teamId, Instant slaDueAt, boolean slaPaused, Instant decidedAt,
                        Instant settledAt, Instant closedAt, long reserveOutstandingMinor, String currency,
                        PendingDecision pendingDecision, int reopenCount, Instant lastEventAt) {
        this.claimId = Objects.requireNonNull(claimId);
        this.claimNumber = claimNumber;
        this.market = market;
        this.lineOfBusiness = lineOfBusiness;
        this.policyNumber = policyNumber;
        this.claimantId = claimantId;
        this.channel = channel;
        this.submittedAt = submittedAt;
        this.status = status;
        this.coverStatus = coverStatus;
        this.handlerId = handlerId;
        this.teamId = teamId;
        this.slaDueAt = slaDueAt;
        this.slaPaused = slaPaused;
        this.decidedAt = decidedAt;
        this.settledAt = settledAt;
        this.closedAt = closedAt;
        this.reserveOutstandingMinor = reserveOutstandingMinor;
        this.currency = currency;
        this.pendingDecision = pendingDecision;
        this.reopenCount = reopenCount;
        this.lastEventAt = lastEventAt;
    }

    /** First event of every claim. */
    public static ClaimSummary fromSubmitted(EventEnvelope e) {
        if (!EventTypes.CLAIM_SUBMITTED.equals(e.type())) {
            throw new IllegalArgumentException("A summary starts with ClaimSubmitted, not " + e.type());
        }
        Map<String, Object> d = e.data();
        return new ClaimSummary(e.claimId(), str(d, "claimNumber"), e.marketCode(), e.lineOfBusiness(),
                str(d, "policyNumber"), str(d, "claimantId"), str(d, "channel"), e.occurredAt(), "SUBMITTED", "PENDING",
                null, null, instant(d, "slaDueAt").orElse(e.occurredAt()), false, null, null, null, 0,
                str(d, "currency"), null, 0, e.occurredAt());
    }

    /** Folds one event into the summary. Unknown or irrelevant event types are ignored. */
    public void apply(EventEnvelope e) {
        Map<String, Object> d = e.data();
        Instant at = e.occurredAt();
        switch (e.type()) {
            case EventTypes.CLAIM_TRIAGED -> {
                status = "TRIAGED";
                coverStatus = Optional.ofNullable(str(d, "coverStatus")).orElse(coverStatus);
            }
            case EventTypes.COVER_DECLINED -> coverStatus = "DECLINED";
            case EventTypes.CLAIM_ASSIGNED -> {
                handlerId = str(d, "handlerId");
                teamId = str(d, "teamId");
                if ("SUBMITTED".equals(status) || "TRIAGED".equals(status)) {
                    status = "ASSIGNED";
                }
            }
            case EventTypes.ASSESSMENT_STARTED -> status = "UNDER_ASSESSMENT";
            case EventTypes.INFORMATION_REQUESTED -> {
                status = "AWAITING_INFO";
                slaPaused = true;
            }
            case EventTypes.INFORMATION_PROVIDED, EventTypes.INFORMATION_REQUEST_EXPIRED -> {
                status = "UNDER_ASSESSMENT";
                slaPaused = false;
                instant(d, "slaDueAt").ifPresent(due -> slaDueAt = due);
            }
            case EventTypes.RESERVE_CHANGED -> {
                reserveOutstandingMinor = lng(d, "newOutstandingMinor").orElse(reserveOutstandingMinor);
                currency = Optional.ofNullable(str(d, "currency")).orElse(currency);
            }
            case EventTypes.DECISION_PROPOSED -> {
                status = "DECISION_PENDING";
                if (Boolean.TRUE.equals(d.get("approvalRequired"))) {
                    pendingDecision = new PendingDecision(UUID.fromString(str(d, "decisionId")), str(d, "outcome"),
                            lng(d, "amountMinor").orElse(0L), str(d, "actorId"), at);
                }
            }
            case EventTypes.DECISION_APPROVED -> pendingDecision = null;
            case EventTypes.DECISION_RETURNED -> {
                pendingDecision = null;
                status = "UNDER_ASSESSMENT";
            }
            case EventTypes.CLAIM_APPROVED -> decided("APPROVED", at);
            case EventTypes.CLAIM_REJECTED -> decided("REJECTED", at);
            case EventTypes.SETTLEMENT_INSTRUCTED -> status = "SETTLEMENT_IN_PROGRESS";
            case EventTypes.CLAIM_SETTLED -> {
                status = "SETTLED";
                settledAt = at;
            }
            case EventTypes.CLAIM_WITHDRAWN -> {
                status = "WITHDRAWN";
                slaPaused = false;
            }
            case EventTypes.CLAIM_REOPENED -> {
                status = "UNDER_ASSESSMENT";
                reopenCount++;
                decidedAt = null;
                closedAt = null;
            }
            case EventTypes.CLAIM_CLOSED -> {
                status = "CLOSED";
                closedAt = at;
            }
            default -> {
                // AssessmentRecorded, PaymentConfirmed, PaymentFailed... don't change the summary
            }
        }
        if (lastEventAt == null || at.isAfter(lastEventAt)) {
            lastEventAt = at;
        }
    }

    private void decided(String outcome, Instant at) {
        status = outcome;
        decidedAt = at;
        pendingDecision = null;
    }

    /** Still being worked (counts towards workload and exposure). */
    public boolean isOpen() {
        return !("CLOSED".equals(status) || "WITHDRAWN".equals(status) || "REJECTED".equals(status) || "SETTLED".equals(status));
    }

    /** SLA runs until a decision is reached. */
    public boolean isSlaRunning() {
        return switch (status) {
            case "SUBMITTED", "TRIAGED", "ASSIGNED", "UNDER_ASSESSMENT", "DECISION_PENDING" -> !slaPaused;
            default -> false;
        };
    }

    private static String str(Map<String, Object> d, String key) {
        Object value = d.get(key);
        return value == null ? null : value.toString();
    }

    private static Optional<Long> lng(Map<String, Object> d, String key) {
        Object value = d.get(key);
        return value instanceof Number n ? Optional.of(n.longValue()) : Optional.empty();
    }

    private static Optional<Instant> instant(Map<String, Object> d, String key) {
        Object value = d.get(key);
        return value == null ? Optional.empty() : Optional.of(Instant.parse(value.toString()));
    }

    // ------------------------------------------------------------------ accessors

    public UUID claimId() { return claimId; }
    public String claimNumber() { return claimNumber; }
    public String market() { return market; }
    public String lineOfBusiness() { return lineOfBusiness; }
    public String policyNumber() { return policyNumber; }
    public String claimantId() { return claimantId; }
    public String channel() { return channel; }
    public Instant submittedAt() { return submittedAt; }
    public String status() { return status; }
    public String coverStatus() { return coverStatus; }
    public String handlerId() { return handlerId; }
    public String teamId() { return teamId; }
    public Instant slaDueAt() { return slaDueAt; }
    public boolean slaPaused() { return slaPaused; }
    public Instant decidedAt() { return decidedAt; }
    public Instant settledAt() { return settledAt; }
    public Instant closedAt() { return closedAt; }
    public long reserveOutstandingMinor() { return reserveOutstandingMinor; }
    public String currency() { return currency; }
    public Optional<PendingDecision> pendingDecision() { return Optional.ofNullable(pendingDecision); }
    public int reopenCount() { return reopenCount; }
    public Instant lastEventAt() { return lastEventAt; }
}
