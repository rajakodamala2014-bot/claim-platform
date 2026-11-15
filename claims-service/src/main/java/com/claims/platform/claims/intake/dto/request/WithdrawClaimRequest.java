package com.claims.platform.claims.intake.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Optional reason for withdrawing the claim")
public record WithdrawClaimRequest(@Size(max = 1000) String reason) {
}
