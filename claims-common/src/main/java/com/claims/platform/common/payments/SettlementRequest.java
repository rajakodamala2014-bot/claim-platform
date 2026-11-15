package com.claims.platform.common.payments;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Message on {@code payments.settlement-requests} (key = claimId). Contract with the external Payments
 * system. {@code instructionId} is unique per attempt (a retry after fixing bank details is a new
 * instruction for the same settlement), so Payments can de-duplicate redeliveries.
 */
public record SettlementRequest(
        UUID instructionId,
        UUID settlementId,
        UUID claimId,
        String claimNumber,
        String payeeName,
        String bankCode,
        String accountNumber,
        long amountMinor,
        String currency,
        Instant requestedAt
) {
    public SettlementRequest {
        Objects.requireNonNull(instructionId, "instructionId");
        Objects.requireNonNull(settlementId, "settlementId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(currency, "currency");
    }
}
