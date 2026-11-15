package com.claims.platform.claims.settlement.application;

import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.Payee;
import com.claims.platform.claims.core.domain.Settlement;

import java.time.Clock;
import java.util.UUID;

/** S12: retry a FAILED payment, optionally with corrected bank details, as a new instruction. */
public class RetrySettlement {
    private final StaffClaims staffClaims;
    private final Clock clock;

    public RetrySettlement(StaffClaims staffClaims, Clock clock) {
        this.staffClaims = staffClaims;
        this.clock = clock;
    }

    public Outcome<Settlement> retry(ClaimId claimId, UUID settlementId, StaffContext staff, long expectedVersion,
                                     Payee correctedPayee) {
        UUID instructionId = UUID.randomUUID();
        return staffClaims.change(claimId, staff, expectedVersion, claim ->
                claim.retrySettlement(settlementId, instructionId, correctedPayee, staff.actor(), clock.instant()));
    }
}
