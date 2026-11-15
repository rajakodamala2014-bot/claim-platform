package com.claims.platform.claims.intake.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "The created claim; replayed is true when this answers a repeated Idempotency-Key")
public record SubmitClaimResponse(UUID claimId, String claimNumber, String status, String coverStatus, boolean replayed) {
    /** The same answer, marked as a replay of an earlier request with this Idempotency-Key. */
    public SubmitClaimResponse asReplay() {
        return new SubmitClaimResponse(claimId, claimNumber, status, coverStatus, true);
    }
}
