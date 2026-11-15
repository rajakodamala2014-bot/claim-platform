package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "The assessment a handler recorded: liability finding, assessed amount and notes")
public record AssessmentResponse(UUID assessmentId, String liability, MoneyResponse assessedAmount, String notes,
                             String assessedBy, Instant assessedAt) {
}
