package com.claims.platform.claims.intake.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.dto.response.ClaimantClaimResponse;


@Schema(description = "A claim with its version (the version travels as ETag, not in the body)")
public record ClaimantClaimDetailResponse(ClaimantClaimResponse claim, long version) {
}
