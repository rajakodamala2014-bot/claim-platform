package com.claims.platform.claims.intake.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A claim in the list of the customer")
public record ClaimantClaimSummaryResponse(UUID claimId, String claimNumber, String status, String statusLabel, String lineOfBusiness,
                   String policyNumber, Instant submittedAt, Instant lastUpdatedAt, boolean actionNeeded) {
}
