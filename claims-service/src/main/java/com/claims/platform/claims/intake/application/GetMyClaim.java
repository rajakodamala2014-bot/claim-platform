package com.claims.platform.claims.intake.application;

import com.claims.platform.claims.core.application.ClaimQueries;
import com.claims.platform.claims.core.application.ClaimantClaims;
import com.claims.platform.claims.core.application.ClaimantViews;
import com.claims.platform.claims.core.application.ClaimantViews.ClaimView;
import com.claims.platform.claims.core.application.ClaimantViews.TimelineEntry;
import com.claims.platform.claims.core.application.DocumentRepository;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimId;

import java.util.List;

/** C5 detail and C6 timeline of one of my claims (404 for anyone else's). */
public class GetMyClaim {
    private final ClaimantClaims claims;
    private final DocumentRepository documents;
    private final ClaimQueries queries;

    public GetMyClaim(ClaimantClaims claims, DocumentRepository documents, ClaimQueries queries) {
        this.claims = claims;
        this.documents = documents;
        this.queries = queries;
    }

    public record Detail(ClaimView claim, long version) {
    }

    public Detail detail(ClaimId claimId, String claimantId) {
        Claim claim = claims.loadOwned(claimId, claimantId);
        return new Detail(ClaimantViews.claim(claim, documents.findByClaim(claimId)), claim.version());
    }

    public List<TimelineEntry> timeline(ClaimId claimId, String claimantId) {
        claims.loadOwned(claimId, claimantId);
        return ClaimantViews.timeline(queries.events(claimId, true));
    }
}
