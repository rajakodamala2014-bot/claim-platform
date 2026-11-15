package com.claims.platform.claims.settlement.dto.request;

import com.claims.platform.claims.core.dto.request.MoneyRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Who to pay and how much")
public record InstructSettlementRequest(@NotNull @Valid PayeeRequest payee, @NotNull @Valid MoneyRequest amount) {
}
