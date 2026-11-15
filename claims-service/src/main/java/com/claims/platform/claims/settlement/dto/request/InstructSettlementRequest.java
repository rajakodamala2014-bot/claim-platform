package com.claims.platform.claims.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Who to pay and how much")
public record InstructSettlementRequest(@NotNull @Valid PayeeRequest payee, @NotNull @Valid SettlementAmountRequest amount) {
}
