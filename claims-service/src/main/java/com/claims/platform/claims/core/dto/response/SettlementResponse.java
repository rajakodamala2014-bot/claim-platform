package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Settlement of a claim; the account number is masked")
public record SettlementResponse(UUID settlementId, MoneyResponse amount, String payeeName, String bankCode,
                             String accountNumberMasked, UUID instructionId, int attempts, String status,
                             String paymentRef, String failureReason, Instant instructedAt, Instant completedAt) {
}
