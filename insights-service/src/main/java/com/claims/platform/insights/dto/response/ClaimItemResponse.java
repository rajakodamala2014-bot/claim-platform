package com.claims.platform.insights.dto.response;

import com.claims.platform.insights.entity.SlaState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A claim as listed in queues, search results and the SLA watch")
public record ClaimItemResponse(
        UUID claimId,
        @Schema(example = "SG-MTR-2026-000123") String claimNumber,
        @Schema(example = "SG") String market,
        @Schema(example = "MOTOR", allowableValues = {"MOTOR", "PROPERTY"}) String lineOfBusiness,
        @Schema(example = "SG-MTR-POL-1001") String policyNumber,
        @Schema(example = "UNDER_ASSESSMENT") String status,
        @Schema(description = "The policy did not cover the incident; a handler must look at it") boolean coverDeclined,
        @Schema(description = "Null while the claim is unassigned") String handlerId,
        String teamId,
        Instant submittedAt,
        @Schema(description = "Deadline for a decision") Instant slaDueAt,
        @Schema(description = "Where the claim stands against its SLA") SlaState slaState,
        @Schema(description = "Outstanding reserve") MoneyResponse reserve) {
}
