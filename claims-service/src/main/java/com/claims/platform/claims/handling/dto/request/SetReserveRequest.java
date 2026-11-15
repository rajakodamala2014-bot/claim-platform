package com.claims.platform.claims.handling.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "New total reserve of a claim and why it changed")
public record SetReserveRequest(@NotNull @Valid MoneyAmountRequest amount, @NotBlank @Size(max = 500) String reason) {
}
