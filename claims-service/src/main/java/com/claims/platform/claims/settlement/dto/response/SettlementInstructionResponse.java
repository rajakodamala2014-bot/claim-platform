package com.claims.platform.claims.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.dto.response.MoneyResponse;
import com.claims.platform.claims.core.entity.Settlement;
import java.util.UUID;

@Schema(description = "The payment instruction sent to the Payments system")
public record SettlementInstructionResponse(UUID settlementId, UUID instructionId, int attempt, MoneyResponse amount, String paymentStatus) {
    public static SettlementInstructionResponse of(Settlement s) {
        return new SettlementInstructionResponse(s.id(), s.instructionId(), s.attempts(), MoneyResponse.of(s.amount()), s.status().name());
    }
}
