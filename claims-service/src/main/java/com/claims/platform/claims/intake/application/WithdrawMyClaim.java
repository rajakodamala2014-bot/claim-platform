package com.claims.platform.claims.intake.application;

import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.ClaimantClaims;
import com.claims.platform.claims.core.application.UnitOfWork;
import com.claims.platform.claims.core.domain.Actor;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimId;

import java.time.Clock;

/** C9: the customer withdraws before a decision. */
public class WithdrawMyClaim {
    private final ClaimantClaims claimantClaims;
    private final ClaimRepository claims;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public WithdrawMyClaim(ClaimantClaims claimantClaims, ClaimRepository claims, UnitOfWork unitOfWork, Clock clock) {
        this.claimantClaims = claimantClaims;
        this.claims = claims;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public String withdraw(ClaimId claimId, Actor claimant, String reason) {
        return unitOfWork.inTransaction(() -> {
            Claim claim = claimantClaims.loadOwned(claimId, claimant.id());
            claim.withdraw(reason, claimant, clock.instant());
            claims.save(claim);
            return claim.status().name();
        });
    }
}
