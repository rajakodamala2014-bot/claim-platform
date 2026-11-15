package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.ClaimId;

import java.time.Clock;

/**
 * S4: pick up (no assignee: the caller takes it) or assign/reassign (supervisors). If two handlers pick up at
 * the same moment, the second fails with CLAIM_ALREADY_ASSIGNED (or STALE_VERSION if it raced the write).
 */
public class AssignClaim {
    private final StaffClaims staffClaims;
    private final Clock clock;

    public AssignClaim(StaffClaims staffClaims, Clock clock) {
        this.staffClaims = staffClaims;
        this.clock = clock;
    }

    public Outcome<String> assign(ClaimId claimId, StaffContext staff, long expectedVersion, String assigneeId, String teamId) {
        String assignee = assigneeId == null || assigneeId.isBlank() ? staff.actor().id() : assigneeId;
        String team = teamId == null || teamId.isBlank() ? staff.teamId() : teamId;
        return staffClaims.change(claimId, staff, expectedVersion, claim -> {
            claim.assign(assignee, team, staff.actor(), clock.instant());
            return assignee;
        });
    }
}
