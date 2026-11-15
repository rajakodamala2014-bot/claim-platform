package com.claims.platform.claims.core.application;

import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;

/**
 * Loads a claim on behalf of a claimant. Someone else's claim is reported as CLAIM_NOT_FOUND (404), never as
 * forbidden, so claim ids can't be probed.
 */
public class ClaimantClaims {
    private final ClaimRepository claims;

    public ClaimantClaims(ClaimRepository claims) {
        this.claims = claims;
    }

    public Claim loadOwned(ClaimId claimId, String claimantId) {
        return claims.findById(claimId)
                .filter(claim -> claim.claimantId().equals(claimantId))
                .orElseThrow(() -> new DomainException(ErrorCode.CLAIM_NOT_FOUND, "Claim " + claimId + " not found"));
    }
}
