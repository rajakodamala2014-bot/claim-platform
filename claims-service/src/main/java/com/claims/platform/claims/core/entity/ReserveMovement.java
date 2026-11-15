package com.claims.platform.claims.core.entity;

import com.claims.platform.common.money.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One immutable ledger line. {@code amount} is signed: positive raises the reserve, negative lowers it.
 * {@code balanceAfter} is stored for audit convenience and verified when a ledger is restored.
 */
public record ReserveMovement(
        UUID id,
        Type type,
        Money amount,
        Money balanceAfter,
        String reason,
        String actorId,
        Instant at
) {
    public enum Type {
        /** First reserve on the claim. */
        INITIAL,
        /** Handler changed the estimate. */
        ADJUSTMENT,
        /** Exposure removed because the claim was settled, rejected or withdrawn. */
        RELEASE
    }

    public ReserveMovement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(balanceAfter, "balanceAfter");
        Objects.requireNonNull(at, "at");
        amount.requireSameCurrency(balanceAfter);
        if (amount.isZero()) {
            throw new IllegalArgumentException("A reserve movement must change the balance");
        }
    }
}
