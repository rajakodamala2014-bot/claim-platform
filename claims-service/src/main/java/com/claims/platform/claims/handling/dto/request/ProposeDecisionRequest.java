package com.claims.platform.claims.handling.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.entity.Decision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Proposal to approve (with an amount) or reject (with a reason code) a claim")
public record ProposeDecisionRequest(@NotNull Decision.Outcome outcome, @Valid MoneyAmountRequest amount, @Size(max = 60) String reasonCode) {
}
