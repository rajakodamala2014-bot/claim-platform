package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.Decision;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;

import java.time.Clock;
import java.util.UUID;

/**
 * S10: a team lead (or manager) approves or returns a pending decision. The approver's own limit must cover
 * the amount (else AMOUNT_EXCEEDS_AUTHORITY: escalate to a manager); the proposer can't approve their own.
 */
public class ReviewDecision {
    public enum Action { APPROVE, RETURN }

    private final StaffClaims staffClaims;
    private final AuthorityLimits authority;
    private final Clock clock;

    public ReviewDecision(StaffClaims staffClaims, AuthorityLimits authority, Clock clock) {
        this.staffClaims = staffClaims;
        this.authority = authority;
        this.clock = clock;
    }

    public Outcome<Action> review(ClaimId claimId, UUID decisionId, StaffContext staff, long expectedVersion,
                                  Action action, String comment) {
        return staffClaims.change(claimId, staff, expectedVersion, claim -> {
            Decision pending = claim.decision()
                    .filter(d -> d.id().equals(decisionId) && d.status() == Decision.Status.AWAITING_APPROVAL)
                    .orElseThrow(() -> new DomainException(ErrorCode.DECISION_NOT_FOUND,
                            "No decision " + decisionId + " is awaiting approval"));
            if (action == Action.APPROVE) {
                // the more fundamental rule first: nobody approves their own proposal, whatever their limit
                if (pending.proposedBy().equals(staff.actor().id())) {
                    throw new DomainException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED, "You cannot approve your own proposal");
                }
                authority.currentPolicy().requireCanApprove(staff.actor(), claim.market(), claim.lineOfBusiness(),
                        pending.amount());
                claim.approveDecision(decisionId, staff.actor(), clock.instant());
            } else {
                claim.returnDecision(decisionId, comment, staff.actor(), clock.instant());
            }
            return action;
        });
    }
}
