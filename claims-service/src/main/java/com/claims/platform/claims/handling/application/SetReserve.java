package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.common.money.Money;

import java.time.Clock;

/** S6: set the reserve to a new total; the ledger records the movement. Returns the new outstanding reserve. */
public class SetReserve {
    private final StaffClaims staffClaims;
    private final Clock clock;

    public SetReserve(StaffClaims staffClaims, Clock clock) {
        this.staffClaims = staffClaims;
        this.clock = clock;
    }

    public Outcome<Money> set(ClaimId claimId, StaffContext staff, long expectedVersion, Money newTotal, String reason) {
        return staffClaims.change(claimId, staff, expectedVersion, claim -> {
            claim.changeReserve(newTotal, reason, staff.actor(), clock.instant());
            return claim.outstandingReserve();
        });
    }
}
