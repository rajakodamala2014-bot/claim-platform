package com.claims.platform.claims.intake.application;

import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.ClaimantClaims;
import com.claims.platform.claims.core.application.UnitOfWork;
import com.claims.platform.claims.core.domain.Actor;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimId;

import java.time.Clock;

/** C10: the customer appeals a rejection, which reopens the claim. */
public class AppealMyClaim {
    private final ClaimantClaims claimantClaims;
    private final ClaimRepository claims;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public AppealMyClaim(ClaimantClaims claimantClaims, ClaimRepository claims, UnitOfWork unitOfWork, Clock clock) {
        this.claimantClaims = claimantClaims;
        this.claims = claims;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public String appeal(ClaimId claimId, Actor claimant, String reason) {
        return unitOfWork.inTransaction(() -> {
            Claim claim = claimantClaims.loadOwned(claimId, claimant.id());
            claim.appeal(reason, claimant, clock.instant());
            claims.save(claim);
            return claim.status().name();
        });
    }
}
