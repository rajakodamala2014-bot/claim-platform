package com.claims.platform.claims.core.domain;

import com.claims.platform.common.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReserveLedgerTest {
    private static final Instant NOW = Instant.parse("2026-11-14T02:00:00Z");
    private final ReserveLedger ledger = ReserveLedger.empty("SGD");

    @Test
    void startsEmptyWithZeroOutstanding() {
        assertThat(ledger.outstanding()).isEqualTo(sgd("0"));
        assertThat(ledger.movements()).isEmpty();
    }

    @Test
    void firstMovementIsInitialThenAdjustmentsAppendTheDifference() {
        ReserveMovement initial = ledger.adjustTo(sgd("5000"), "Initial estimate", "h-arun", NOW).orElseThrow();
        ReserveMovement up = ledger.adjustTo(sgd("7500"), "Repair quote higher", "h-arun", NOW).orElseThrow();
        ReserveMovement down = ledger.adjustTo(sgd("4200"), "Assessed", "h-arun", NOW).orElseThrow();

        assertThat(initial.type()).isEqualTo(ReserveMovement.Type.INITIAL);
        assertThat(initial.amount()).isEqualTo(sgd("5000"));
        assertThat(up.type()).isEqualTo(ReserveMovement.Type.ADJUSTMENT);
        assertThat(up.amount()).isEqualTo(sgd("2500"));
        assertThat(down.amount()).isEqualTo(sgd("-3300"));
        assertThat(down.balanceAfter()).isEqualTo(sgd("4200"));
        assertThat(ledger.outstanding()).isEqualTo(sgd("4200"));
        assertThat(ledger.movements()).hasSize(3);
    }

    @Test
    void outstandingIsAlwaysTheSumOfMovements() {
        ledger.adjustTo(sgd("100.10"), "a", "x", NOW);
        ledger.adjustTo(sgd("0.05"), "b", "x", NOW);
        ledger.adjustTo(sgd("999.99"), "c", "x", NOW);

        Money sum = ledger.movements().stream().map(ReserveMovement::amount).reduce(sgd("0"), Money::plus);
        assertThat(ledger.outstanding()).isEqualTo(sum).isEqualTo(sgd("999.99"));
    }

    @Test
    void unchangedTotalAppendsNothing() {
        ledger.adjustTo(sgd("5000"), "Initial", "x", NOW);

        assertThat(ledger.adjustTo(sgd("5000"), "Same again", "x", NOW)).isEmpty();
        assertThat(ledger.movements()).hasSize(1);
    }

    @Test
    void releaseBringsExposureToZeroAndIsIdempotent() {
        ledger.adjustTo(sgd("5000"), "Initial", "x", NOW);

        ReserveMovement release = ledger.release("Claim settled", "system", NOW).orElseThrow();

        assertThat(release.type()).isEqualTo(ReserveMovement.Type.RELEASE);
        assertThat(release.amount()).isEqualTo(sgd("-5000"));
        assertThat(ledger.outstanding().isZero()).isTrue();
        assertThat(ledger.release("again", "system", NOW)).isEmpty();
    }

    @Test
    void historyIsAppendOnly() {
        ledger.adjustTo(sgd("5000"), "Initial", "x", NOW);

        assertThatThrownBy(() -> ledger.movements().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ledger.movements().remove(0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNegativeTotalMissingReasonAndForeignCurrency() {
        assertThatThrownBy(() -> ledger.adjustTo(sgd("-1"), "x", "x", NOW))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> ledger.adjustTo(sgd("1"), " ", "x", NOW))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> ledger.adjustTo(Money.of("1", "HKD"), "x", "x", NOW))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.CURRENCY_MISMATCH);
        assertThat(ledger.movements()).isEmpty();
    }

    @Test
    void restoreRebuildsAndVerifiesTheLedger() {
        ledger.adjustTo(sgd("5000"), "Initial", "x", NOW);
        ledger.adjustTo(sgd("3000"), "Lower", "x", NOW);

        ReserveLedger restored = ReserveLedger.restore("SGD", ledger.movements());

        assertThat(restored.outstanding()).isEqualTo(sgd("3000"));
        assertThat(restored.movements()).containsExactlyElementsOf(ledger.movements());
    }

    @Test
    void restoreRejectsATamperedLedger() {
        ReserveMovement wrongBalance = new ReserveMovement(UUID.randomUUID(), ReserveMovement.Type.INITIAL,
                sgd("5000"), sgd("4000"), "Initial", "x", NOW);
        ReserveMovement belowZero = new ReserveMovement(UUID.randomUUID(), ReserveMovement.Type.ADJUSTMENT,
                sgd("-1"), sgd("-1"), "x", "x", NOW);

        assertThatThrownBy(() -> ReserveLedger.restore("SGD", List.of(wrongBalance))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ReserveLedger.restore("SGD", List.of(belowZero))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void movementMustChangeTheBalance() {
        assertThatThrownBy(() -> new ReserveMovement(UUID.randomUUID(), ReserveMovement.Type.ADJUSTMENT,
                sgd("0"), sgd("0"), "x", "x", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Money sgd(String major) {
        return Money.of(major, "SGD");
    }
}
