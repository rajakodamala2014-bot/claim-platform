package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "The decision on a claim as the customer sees it")
public record ClaimantDecisionResponse(String outcome, MoneyResponse approvedAmount, String reasonCode, Instant decidedAt) {
}
