package com.claims.platform.claims.handling.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.dto.request.MoneyRequest;
import com.claims.platform.claims.core.entity.Assessment;
import com.claims.platform.claims.core.entity.Decision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Proposal to approve a fully or partially covered amount, or to reject with a reason code")
public record ProposeDecisionRequest(@NotNull Decision.Outcome outcome, Assessment.Liability coverage,
                                      @Valid MoneyRequest amount, @Size(max = 60) String reasonCode) {
}
