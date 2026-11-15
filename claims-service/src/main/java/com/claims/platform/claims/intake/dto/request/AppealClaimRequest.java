package com.claims.platform.claims.intake.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Why the customer disagrees with the rejection")
public record AppealClaimRequest(@NotBlank @Size(max = 2000) String reason) {
}
