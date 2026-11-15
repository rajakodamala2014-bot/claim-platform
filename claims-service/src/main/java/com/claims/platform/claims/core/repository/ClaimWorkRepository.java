package com.claims.platform.claims.core.repository;

import com.claims.platform.claims.core.entity.ClaimId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Finds claims that background jobs must act on. Each job re-checks the claim before changing it. */
public interface ClaimWorkRepository {

    /** SUBMITTED claims whose cover check is still pending (oldest first). */
    List<ClaimId> findPendingCoverChecks(int limit);

    /** Open information requests whose due date has passed. */
    List<DueInformationRequest> findDueInformationRequests(Instant now, int limit);

    /** SETTLED / REJECTED / WITHDRAWN claims whose market cooling-off period has elapsed. */
    List<ClaimId> findClosableClaims(Instant now, int limit);

    record DueInformationRequest(ClaimId claimId, UUID requestId) {
    }
}
