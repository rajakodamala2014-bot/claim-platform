package com.claims.platform.claims.core.entity;

import com.claims.platform.common.money.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A proposed approve/reject decision. {@code amount} is the approved amount (APPROVE) or the amount at
 * stake used for the authority check (REJECT).
 */
public record Decision(
        UUID id,
        Outcome outcome,
        Money amount,
        String reasonCode,
        String proposedBy,
        Instant proposedAt,
        ApprovalRequirement approval,
        Status status,
        String decidedBy,
        Instant decidedAt
) {
    public enum Outcome { APPROVE, REJECT }

    public enum Status {
        /** Waiting in a team lead's approvals inbox. */
        AWAITING_APPROVAL,
        /** In force: either within the proposer's authority or approved by a supervisor. */
        FINAL,
        /** Sent back by the supervisor; the claim returned to UNDER_ASSESSMENT. */
        RETURNED,
        /** Superseded by an appeal; kept for history. */
        SUPERSEDED
    }

    public Decision {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(proposedBy, "proposedBy");
        Objects.requireNonNull(approval, "approval");
        Objects.requireNonNull(status, "status");
    }

    Decision finalise(String by, Instant at) {
        return new Decision(id, outcome, amount, reasonCode, proposedBy, proposedAt, approval, Status.FINAL, by, at);
    }

    Decision returned(String by, Instant at) {
        return new Decision(id, outcome, amount, reasonCode, proposedBy, proposedAt, approval, Status.RETURNED, by, at);
    }

    Decision superseded() {
        return new Decision(id, outcome, amount, reasonCode, proposedBy, proposedAt, approval, Status.SUPERSEDED,
                decidedBy, decidedAt);
    }
}
