package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.ClaimantClaims;
import com.claims.platform.claims.core.application.ClaimantViews;
import com.claims.platform.claims.core.application.ClaimantViews.InfoRequestView;
import com.claims.platform.claims.core.domain.ClaimId;

import java.util.List;

/** C7: what the company is asking the customer for (newest first). */
public class MyInformationRequests {
    private final ClaimantClaims claims;

    public MyInformationRequests(ClaimantClaims claims) {
        this.claims = claims;
    }

    public List<InfoRequestView> list(ClaimId claimId, String claimantId) {
        return claims.loadOwned(claimId, claimantId).informationRequests().reversed().stream()
                .map(ClaimantViews::infoRequest)
                .toList();
    }
}
