package com.claims.platform.claims.core.service;

import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;

/** Loads a claim on behalf of a claimant. Someone else's claim is reported as CLAIM_NOT_FOUND (404), never as forbidden, so claim ids cannot be probed. */
public interface ClaimantClaimAccessService {
    /**
     * Loads a claim on behalf of a claimant. Someone else's claim is reported as CLAIM_NOT_FOUND (404), never as
     * forbidden, so claim ids can't be probed.
     */
    Claim loadOwnedClaim(ClaimId claimId, String claimantId);
}
