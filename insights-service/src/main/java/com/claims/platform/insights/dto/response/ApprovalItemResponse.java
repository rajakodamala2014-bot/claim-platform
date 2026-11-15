package com.claims.platform.insights.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A decision waiting for approval")
public record ApprovalItemResponse(
        UUID claimId,
        @Schema(example = "SG-MTR-2026-000123") String claimNumber,
        @Schema(example = "SG") String market,
        @Schema(example = "MOTOR") String lineOfBusiness,
        UUID decisionId,
        @Schema(example = "APPROVE", allowableValues = {"APPROVE", "REJECT"}) String outcome,
        MoneyResponse amount,
        @Schema(description = "Handler who proposed the decision") String proposedBy,
        Instant proposedAt,
        String teamId) {
}
