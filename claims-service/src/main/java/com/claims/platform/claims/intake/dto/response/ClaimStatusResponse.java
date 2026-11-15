package com.claims.platform.claims.intake.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "The claim and its new status")
public record ClaimStatusResponse(UUID claimId, String status) {
}
