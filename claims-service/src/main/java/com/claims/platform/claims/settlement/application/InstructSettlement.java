package com.claims.platform.claims.settlement.application;

import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.Payee;
import com.claims.platform.claims.core.domain.Settlement;
import com.claims.platform.common.money.Money;

import java.time.Clock;
import java.util.UUID;

/**
 * S11: instruct payment (amount <= approved). APPROVED -> SETTLEMENT_IN_PROGRESS. The payment instruction is
 * written to the outbox for {@code payments.settlement-requests} in the same transaction.
 */
public class InstructSettlement {
    private final StaffClaims staffClaims;
    private final Clock clock;

    public InstructSettlement(StaffClaims staffClaims, Clock clock) {
        this.staffClaims = staffClaims;
        this.clock = clock;
    }

    public Outcome<Settlement> instruct(ClaimId claimId, StaffContext staff, long expectedVersion, Payee payee, Money amount) {
        UUID settlementId = UUID.randomUUID();
        UUID instructionId = UUID.randomUUID();
        return staffClaims.change(claimId, staff, expectedVersion, claim ->
                claim.instructSettlement(settlementId, instructionId, payee, amount, staff.actor(), clock.instant()));
    }
}
