package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Outstanding reserve of a claim and the movements that led to it")
public record ReserveResponse(MoneyResponse outstanding, List<ReserveMovementResponse> movements) {
}
