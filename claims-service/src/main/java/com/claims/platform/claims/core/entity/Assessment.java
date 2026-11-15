package com.claims.platform.claims.core.entity;

import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.common.money.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A handler's assessment: fault finding, assessed amount and notes (notes are internal only). */
public record Assessment(
        UUID id,
        Liability liability,
        Money assessedAmount,
        String notes,
        String assessedBy,
        Instant assessedAt
) {
    /** Who was at fault, from the insurer's point of view. */
    public enum Liability { FULL, PARTIAL, NONE }

    public Assessment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(liability, "liability");
        Objects.requireNonNull(assessedAmount, "assessedAmount");
        if (assessedAmount.isNegative()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Assessed amount cannot be negative");
        }
    }
}
