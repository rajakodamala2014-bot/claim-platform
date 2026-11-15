package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A claim as its customer sees it; never contains reserves, notes or handler ids")
public record ClaimantClaimResponse(
        UUID claimId,
        String claimNumber,
        String status,
        String statusLabel,
        String nextStep,
        String lineOfBusiness,
        String policyNumber,
        Instant incidentAt,
        Instant submittedAt,
        Instant lastUpdatedAt,
        ActionNeededResponse actionNeeded,
        ClaimantDecisionResponse decision,
        ClaimantPaymentResponse payment,
        List<ClaimantDocumentResponse> documents,
        boolean canWithdraw,
        boolean canAppeal
) {
}
