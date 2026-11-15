package com.claims.platform.claims.intake.application;

import com.claims.platform.claims.core.application.ClaimQueries;
import com.claims.platform.claims.core.application.ClaimQueries.ClaimSummary;
import com.claims.platform.claims.core.application.ClaimantViews;
import com.claims.platform.claims.core.application.Page;
import com.claims.platform.claims.core.application.PageRequest;
import com.claims.platform.claims.core.domain.ClaimStatus;

import java.time.Instant;
import java.util.UUID;

/** C4: my claims, newest first. */
public class GetMyClaims {
    private final ClaimQueries queries;

    public GetMyClaims(ClaimQueries queries) {
        this.queries = queries;
    }

    public record Item(UUID claimId, String claimNumber, String status, String statusLabel, String lineOfBusiness,
                       String policyNumber, Instant submittedAt, Instant lastUpdatedAt, boolean actionNeeded) {
    }

    public Page<Item> list(String claimantId, PageRequest page) {
        return queries.claimsOfClaimant(claimantId, page).map(GetMyClaims::item);
    }

    private static Item item(ClaimSummary s) {
        return new Item(s.claimId(), s.claimNumber(), s.status(), ClaimantViews.statusLabel(ClaimStatus.valueOf(s.status())),
                s.lineOfBusiness(), s.policyNumber(), s.submittedAt(), s.statusChangedAt(), s.actionNeeded());
    }
}
