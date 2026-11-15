package com.claims.platform.claims.handling.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.handling.entity.ReviewAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Approve or return a decision that awaits approval")
public record ReviewDecisionRequest(@NotNull ReviewAction action, @Size(max = 1000) String comment) {
}
