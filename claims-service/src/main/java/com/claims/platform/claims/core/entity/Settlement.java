package com.claims.platform.claims.core.entity;

import com.claims.platform.common.money.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Payment instruction for an approved claim. Each attempt has its own {@code instructionId}; results for an
 * older attempt are ignored, so a late duplicate from the Payments system can't overwrite a retry.
 */
public record Settlement(
        UUID id,
        Money amount,
        Payee payee,
        UUID instructionId,
        int attempts,
        Status status,
        String paymentRef,
        String failureReason,
        Instant instructedAt,
        Instant completedAt
) {
    public enum Status { INSTRUCTED, PAID, FAILED }

    public Settlement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(payee, "payee");
        Objects.requireNonNull(instructionId, "instructionId");
        Objects.requireNonNull(status, "status");
    }

    Settlement retried(UUID newInstructionId, Payee newPayee, Instant at) {
        return new Settlement(id, amount, newPayee, newInstructionId, attempts + 1, Status.INSTRUCTED, null, null, at, null);
    }

    Settlement paid(String reference, Instant at) {
        return new Settlement(id, amount, payee, instructionId, attempts, Status.PAID, reference, null, instructedAt, at);
    }

    Settlement failed(String reason, Instant at) {
        return new Settlement(id, amount, payee, instructionId, attempts, Status.FAILED, null, reason, instructedAt, at);
    }
}
