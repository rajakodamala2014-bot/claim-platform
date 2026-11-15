package com.claims.platform.insights.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Money at risk in one market and line of business")
public record ExposureItemResponse(
        @Schema(example = "SG") String market,
        @Schema(example = "MOTOR") String lineOfBusiness,
        @Schema(example = "42") int outstandingClaims,
        MoneyResponse totalReserve) {
}
