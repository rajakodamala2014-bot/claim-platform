package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.ClaimId;

import java.time.Clock;

/** S5: ASSIGNED -> UNDER_ASSESSMENT. */
public class StartAssessment {
    private final StaffClaims staffClaims;
    private final Clock clock;

    public StartAssessment(StaffClaims staffClaims, Clock clock) {
        this.staffClaims = staffClaims;
        this.clock = clock;
    }

    public Outcome<Void> start(ClaimId claimId, StaffContext staff, long expectedVersion) {
        return staffClaims.change(claimId, staff, expectedVersion, claim -> {
            claim.startAssessment(staff.actor(), clock.instant());
            return null;
        });
    }
}
