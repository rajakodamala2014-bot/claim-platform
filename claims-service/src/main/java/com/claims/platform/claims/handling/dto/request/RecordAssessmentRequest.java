package com.claims.platform.claims.handling.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.dto.request.MoneyRequest;
import com.claims.platform.claims.core.entity.Assessment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Liability finding, assessed amount and notes of a handler")
public record RecordAssessmentRequest(@NotNull Assessment.Liability liability, @NotNull @Valid MoneyRequest assessedAmount,
                                @Size(max = 4000) String notes) {
}
