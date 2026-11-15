package com.claims.platform.insights.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An amount of money")
public record MoneyResponse(
        @Schema(description = "Amount in major units", example = "12000.00") String amount,
        @Schema(description = "Amount in minor units (cents)", example = "1200000") long amountMinor,
        @Schema(description = "ISO 4217 currency code", example = "SGD") String currency) {
}
