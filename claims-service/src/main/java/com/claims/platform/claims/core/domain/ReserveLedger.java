package com.claims.platform.claims.core.domain;

import com.claims.platform.common.money.CurrencyMismatchException;
import com.claims.platform.common.money.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-only reserve ledger of one claim. Outstanding exposure is always the sum of the movements:
 * nothing is ever updated or deleted, so the history of every estimate change is preserved.
 */
public final class ReserveLedger {
    private final String currency;
    private final List<ReserveMovement> movements;

    private ReserveLedger(String currency, List<ReserveMovement> movements) {
        this.currency = Money.zero(currency).currency();
        this.movements = new ArrayList<>(movements);
    }

    public static ReserveLedger empty(String currency) {
        return new ReserveLedger(currency, List.of());
    }

    /** Rebuilds a ledger from storage, verifying it is internally consistent. */
    public static ReserveLedger restore(String currency, List<ReserveMovement> movements) {
        ReserveLedger ledger = empty(currency);
        Money running = Money.zero(ledger.currency);
        for (ReserveMovement movement : movements) {
            running = running.plus(movement.amount());
            if (!running.equals(movement.balanceAfter()) || running.isNegative()) {
                throw new IllegalStateException("Corrupt reserve ledger at movement " + movement.id());
            }
            ledger.movements.add(movement);
        }
        return ledger;
    }

    public Money outstanding() {
        return movements.stream()
                .map(ReserveMovement::amount)
                .reduce(Money.zero(currency), Money::plus);
    }

    public List<ReserveMovement> movements() {
        return Collections.unmodifiableList(movements);
    }

    public String currency() {
        return currency;
    }

    /**
     * Sets the reserve to {@code newTotal} by appending the difference.
     *
     * @return the appended movement, or empty if the total is unchanged
     */
    public Optional<ReserveMovement> adjustTo(Money newTotal, String reason, String actorId, Instant at) {
        Objects.requireNonNull(newTotal, "newTotal");
        requireCurrency(newTotal);
        if (newTotal.isNegative()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Reserve cannot be negative");
        }
        if (reason == null || reason.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "A reason is required to change the reserve");
        }
        Money delta = newTotal.minus(outstanding());
        if (delta.isZero()) {
            return Optional.empty();
        }
        ReserveMovement.Type type = movements.isEmpty() ? ReserveMovement.Type.INITIAL : ReserveMovement.Type.ADJUSTMENT;
        return Optional.of(append(type, delta, reason, actorId, at));
    }

    /** Brings outstanding exposure to zero. @return the release movement, or empty if nothing was reserved */
    public Optional<ReserveMovement> release(String reason, String actorId, Instant at) {
        Money outstanding = outstanding();
        if (outstanding.isZero()) {
            return Optional.empty();
        }
        return Optional.of(append(ReserveMovement.Type.RELEASE, outstanding.negate(), reason, actorId, at));
    }

    private ReserveMovement append(ReserveMovement.Type type, Money delta, String reason, String actorId, Instant at) {
        ReserveMovement movement = new ReserveMovement(UUID.randomUUID(), type, delta, outstanding().plus(delta),
                reason, actorId, at);
        movements.add(movement);
        return movement;
    }

    private void requireCurrency(Money money) {
        if (!currency.equals(money.currency())) {
            CurrencyMismatchException cause = new CurrencyMismatchException(currency, money.currency());
            DomainException ex = new DomainException(ErrorCode.CURRENCY_MISMATCH, cause.getMessage());
            ex.initCause(cause);
            throw ex;
        }
    }
}
