package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "One entry of the append-only reserve ledger")
public record ReserveMovementResponse(UUID id, String type, MoneyResponse amount, MoneyResponse balanceAfter, String reason, String actorId,
                       Instant at) {
}
