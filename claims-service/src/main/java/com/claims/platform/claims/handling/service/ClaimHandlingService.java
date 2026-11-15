package com.claims.platform.claims.handling.service;

import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.entity.Assessment;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.Decision;
import com.claims.platform.claims.core.service.StaffClaimCommandService.Outcome;
import com.claims.platform.claims.handling.entity.ReviewAction;
import com.claims.platform.common.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Staff commands on a claim (S4-S10): assignment, assessment, reserves, information requests and decisions. */
public interface ClaimHandlingService {
    /**
     * S4: pick up (no assignee: the caller takes it) or assign/reassign (supervisors). If two handlers pick up at
     * the same moment, the second fails with CLAIM_ALREADY_ASSIGNED (or STALE_VERSION if it raced the write).
     */
    Outcome<String> assignClaim(ClaimId claimId, StaffContext staff, long expectedVersion, String assigneeId, String teamId);

    /** S5: ASSIGNED -> UNDER_ASSESSMENT. */
    Outcome<Void> startAssessment(ClaimId claimId, StaffContext staff, long expectedVersion);

    /** S6: set the reserve to a new total; the ledger records the movement. Returns the new outstanding reserve. */
    Outcome<Money> setReserve(ClaimId claimId, StaffContext staff, long expectedVersion, Money newTotal, String reason);

    /** S7: ask the customer for documents/answers. Due date defaults to the market setting (info_request_due_days). */
    Outcome<UUID> requestInformation(ClaimId claimId, StaffContext staff, long expectedVersion, List<String> items, Instant dueAt);

    /** S8: fault finding, assessed amount and notes. */
    Outcome<UUID> recordAssessment(ClaimId claimId, StaffContext staff, long expectedVersion, Assessment.Liability liability,
            Money assessedAmount, String notes);

    /**
     * S9: propose approve (amount) or reject (reason). The authority check uses the approved amount, or for a
     * rejection the outstanding reserve (the money at stake). Within the proposer's limit the decision is final
     * at once; above it, it waits for a team lead.
     */
    Outcome<Decision> proposeDecision(ClaimId claimId, StaffContext staff, long expectedVersion, Decision.Outcome outcome,
            Money approvedAmount, String reasonCode);

    /**
     * S10: a team lead (or manager) approves or returns a pending decision. The approver's own limit must cover
     * the amount (else AMOUNT_EXCEEDS_AUTHORITY: escalate to a manager); the proposer can't approve their own.
     */
    Outcome<ReviewAction> reviewDecision(ClaimId claimId, UUID decisionId, StaffContext staff, long expectedVersion,
            ReviewAction action, String comment);
}
