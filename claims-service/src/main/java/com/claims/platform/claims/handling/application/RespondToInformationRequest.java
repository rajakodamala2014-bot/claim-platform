package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.AttachDocuments;
import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.ClaimantClaims;
import com.claims.platform.claims.core.application.UnitOfWork;
import com.claims.platform.claims.core.domain.Actor;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.ValidationException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** C8: the customer answers an information request (text and/or documents). AWAITING_INFO -> UNDER_ASSESSMENT. */
public class RespondToInformationRequest {
    private final ClaimantClaims claimantClaims;
    private final ClaimRepository claims;
    private final AttachDocuments attachDocuments;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RespondToInformationRequest(ClaimantClaims claimantClaims, ClaimRepository claims,
                                       AttachDocuments attachDocuments, UnitOfWork unitOfWork, Clock clock) {
        this.claimantClaims = claimantClaims;
        this.claims = claims;
        this.attachDocuments = attachDocuments;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public String respond(ClaimId claimId, UUID requestId, Actor claimant, String answer, List<UUID> uploadIds) {
        boolean noAnswer = answer == null || answer.isBlank();
        if (noAnswer && (uploadIds == null || uploadIds.isEmpty())) {
            throw ValidationException.of("answer", "send an answer or at least one document");
        }
        return unitOfWork.inTransaction(() -> {
            Claim claim = claimantClaims.loadOwned(claimId, claimant.id());
            List<UUID> documentIds = attachDocuments.attach(uploadIds, claimId, claimant.id());
            claim.provideInformation(requestId, answer, documentIds, claimant, clock.instant());
            claims.save(claim);
            return claim.status().name();
        });
    }
}
