package com.claims.platform.claims.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "An amount of money: decimal amount as text and ISO 4217 currency")
public record SettlementAmountRequest(@NotBlank String amount, @NotBlank @Size(min = 3, max = 3) String currency) {
}
