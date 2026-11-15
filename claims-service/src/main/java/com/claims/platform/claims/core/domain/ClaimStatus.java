package com.claims.platform.claims.core.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/**
 * Claim lifecycle. The transition table below is the single source of truth; {@link Claim} is the only
 * code that changes a claim's status, and it always goes through {@link #requireTransitionTo}.
 * <pre>
 * SUBMITTED -> TRIAGED -> ASSIGNED -> UNDER_ASSESSMENT <-> AWAITING_INFO
 * UNDER_ASSESSMENT -> DECISION_PENDING -> APPROVED -> SETTLEMENT_IN_PROGRESS -> SETTLED -> CLOSED
 * DECISION_PENDING -> REJECTED -> CLOSED          DECISION_PENDING -> UNDER_ASSESSMENT (decision returned)
 * REJECTED -> UNDER_ASSESSMENT (appeal)          SUBMITTED..AWAITING_INFO -> WITHDRAWN -> CLOSED
 * </pre>
 */
public enum ClaimStatus {
    SUBMITTED,
    TRIAGED,
    ASSIGNED,
    UNDER_ASSESSMENT,
    AWAITING_INFO,
    DECISION_PENDING,
    APPROVED,
    REJECTED,
    SETTLEMENT_IN_PROGRESS,
    SETTLED,
    WITHDRAWN,
    CLOSED;

    private static final Map<ClaimStatus, Set<ClaimStatus>> ALLOWED = Map.ofEntries(
            entry(SUBMITTED, EnumSet.of(TRIAGED, WITHDRAWN)),
            entry(TRIAGED, EnumSet.of(ASSIGNED, WITHDRAWN)),
            entry(ASSIGNED, EnumSet.of(UNDER_ASSESSMENT, WITHDRAWN)),
            entry(UNDER_ASSESSMENT, EnumSet.of(AWAITING_INFO, DECISION_PENDING, WITHDRAWN)),
            entry(AWAITING_INFO, EnumSet.of(UNDER_ASSESSMENT, WITHDRAWN)),
            entry(DECISION_PENDING, EnumSet.of(APPROVED, REJECTED, UNDER_ASSESSMENT)),
            entry(APPROVED, EnumSet.of(SETTLEMENT_IN_PROGRESS)),
            entry(REJECTED, EnumSet.of(CLOSED, UNDER_ASSESSMENT)),
            entry(SETTLEMENT_IN_PROGRESS, EnumSet.of(SETTLED)),
            entry(SETTLED, EnumSet.of(CLOSED)),
            entry(WITHDRAWN, EnumSet.of(CLOSED)),
            entry(CLOSED, EnumSet.noneOf(ClaimStatus.class))
    );

    public boolean canTransitionTo(ClaimStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public void requireTransitionTo(ClaimStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidTransitionException(this, target);
        }
    }

    public Set<ClaimStatus> allowedTargets() {
        return Set.copyOf(ALLOWED.get(this));
    }

    /** The customer may still withdraw: no decision has been proposed yet. */
    public boolean isWithdrawable() {
        return canTransitionTo(WITHDRAWN);
    }

    /** Terminal for handling purposes; only closing (or an appeal from REJECTED) remains. */
    public boolean isOutcomeReached() {
        return this == SETTLED || this == REJECTED || this == WITHDRAWN || this == CLOSED;
    }

    /** Statuses the auto-close job closes after the cooling-off period. */
    public boolean isAutoClosable() {
        return canTransitionTo(CLOSED);
    }
}
