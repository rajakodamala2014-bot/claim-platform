package com.claims.platform.claims.core.service.impl;

import com.claims.platform.claims.core.repository.ClaimRepository;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.service.ClaimantClaimAccessService;
import org.springframework.stereotype.Service;

@Service
public class ClaimantClaimAccessServiceImpl implements ClaimantClaimAccessService {
    private final ClaimRepository claims;

    public ClaimantClaimAccessServiceImpl(ClaimRepository claims) {
        this.claims = claims;
    }

    @Override
    public Claim loadOwnedClaim(ClaimId claimId, String claimantId) {
        return claims.findById(claimId)
                .filter(claim -> claim.claimantId().equals(claimantId))
                .orElseThrow(() -> new DomainException(ErrorCode.CLAIM_NOT_FOUND, "Claim " + claimId + " not found"));
    }
}
