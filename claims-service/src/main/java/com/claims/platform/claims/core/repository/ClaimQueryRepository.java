package com.claims.platform.claims.core.repository;

import com.claims.platform.claims.core.common.Page;
import com.claims.platform.claims.core.common.PageRequest;
import com.claims.platform.claims.core.entity.ClaimEvent;
import com.claims.platform.claims.core.entity.ClaimId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-side queries that don't need the full aggregate. */
public interface ClaimQueryRepository {

    /** A claimant's claims, newest first, keyset-paginated. */
    Page<ClaimSummary> findClaimsOfClaimant(String claimantId, PageRequest page);

    /** Events of a claim in the order they happened; {@code claimantVisibleOnly} gives the customer timeline. */
    List<ClaimEvent> events(ClaimId claimId, boolean claimantVisibleOnly);

    record ClaimSummary(
            UUID claimId,
            String claimNumber,
            String status,
            String lineOfBusiness,
            String policyNumber,
            Instant submittedAt,
            Instant statusChangedAt,
            boolean actionNeeded
    ) {
    }
}
