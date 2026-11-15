package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.ApprovalRequirement;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.Decision;
import com.claims.platform.common.money.Money;

import java.time.Clock;
import java.util.UUID;

/**
 * S9: propose approve (amount) or reject (reason). The authority check uses the approved amount, or for a
 * rejection the outstanding reserve (the money at stake). Within the proposer's limit the decision is final
 * at once; above it, it waits for a team lead.
 */
public class ProposeDecision {
    private final StaffClaims staffClaims;
    private final AuthorityLimits authority;
    private final Clock clock;

    public ProposeDecision(StaffClaims staffClaims, AuthorityLimits authority, Clock clock) {
        this.staffClaims = staffClaims;
        this.authority = authority;
        this.clock = clock;
    }

    public Outcome<Decision> propose(ClaimId claimId, StaffContext staff, long expectedVersion, Decision.Outcome outcome,
                                     Money approvedAmount, String reasonCode) {
        UUID decisionId = UUID.randomUUID();
        return staffClaims.change(claimId, staff, expectedVersion, claim -> {
            Money atStake = outcome == Decision.Outcome.APPROVE ? approvedAmount : claim.outstandingReserve();
            ApprovalRequirement approval = authority.currentPolicy()
                    .requirementFor(staff.actor(), claim.market(), claim.lineOfBusiness(), atStake);
            return claim.proposeDecision(decisionId, outcome, atStake, reasonCode, approval, staff.actor(), clock.instant());
        });
    }
}
