package com.claims.platform.common.payments;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Message on {@code payments.payment-results} (key = claimId), produced by the Payments system. */
public record PaymentResult(
        UUID instructionId,
        UUID settlementId,
        UUID claimId,
        Status status,
        String paymentRef,
        String failureReason,
        long amountMinor,
        String currency,
        Instant processedAt
) {
    public enum Status { PAID, FAILED }

    public PaymentResult {
        Objects.requireNonNull(instructionId, "instructionId");
        Objects.requireNonNull(settlementId, "settlementId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(status, "status");
    }
}
