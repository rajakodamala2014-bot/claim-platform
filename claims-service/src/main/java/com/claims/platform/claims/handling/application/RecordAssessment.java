package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.Assessment;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.common.money.Money;

import java.time.Clock;
import java.util.UUID;

/** S8: fault finding, assessed amount and notes. */
public class RecordAssessment {
    private final StaffClaims staffClaims;
    private final Clock clock;

    public RecordAssessment(StaffClaims staffClaims, Clock clock) {
        this.staffClaims = staffClaims;
        this.clock = clock;
    }

    public Outcome<UUID> record(ClaimId claimId, StaffContext staff, long expectedVersion, Assessment.Liability liability,
                                Money assessedAmount, String notes) {
        UUID assessmentId = UUID.randomUUID();
        return staffClaims.change(claimId, staff, expectedVersion, claim -> {
            claim.recordAssessment(assessmentId, liability, assessedAmount, notes, staff.actor(), clock.instant());
            return assessmentId;
        });
    }
}
