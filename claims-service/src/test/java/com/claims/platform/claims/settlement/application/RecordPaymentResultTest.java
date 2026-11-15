package com.claims.platform.claims.settlement.application;

import com.claims.platform.claims.core.application.InMemoryClaimRepository;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimFixture;
import com.claims.platform.claims.core.domain.ClaimStatus;
import com.claims.platform.common.payments.PaymentResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.claims.platform.claims.core.domain.ClaimFixture.INSTRUCTION_ID;
import static com.claims.platform.claims.core.domain.ClaimFixture.SETTLEMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;

class RecordPaymentResultTest {
    private static final Instant NOW = Instant.parse("2026-11-14T05:00:00Z");
    private final InMemoryClaimRepository repository = new InMemoryClaimRepository();
    private final RecordPaymentResult record = new RecordPaymentResult(repository,
            InMemoryClaimRepository.directUnitOfWork(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void paidSettlesOnceAndDuplicatesAreIgnored() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.SETTLEMENT_IN_PROGRESS));
        PaymentResult paid = result(claim, INSTRUCTION_ID, PaymentResult.Status.PAID);

        assertThat(record.record(paid)).isEqualTo(RecordPaymentResult.Outcome.APPLIED);
        assertThat(record.record(paid)).isEqualTo(RecordPaymentResult.Outcome.IGNORED_DUPLICATE);
        assertThat(claim.status()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(claim.version()).isEqualTo(2);
    }

    @Test
    void failedKeepsTheClaimInProgressAndStaleAttemptsAreIgnored() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.SETTLEMENT_IN_PROGRESS));

        assertThat(record.record(result(claim, UUID.randomUUID(), PaymentResult.Status.PAID)))
                .isEqualTo(RecordPaymentResult.Outcome.IGNORED_DUPLICATE);
        assertThat(record.record(result(claim, INSTRUCTION_ID, PaymentResult.Status.FAILED)))
                .isEqualTo(RecordPaymentResult.Outcome.APPLIED);
        assertThat(claim.status()).isEqualTo(ClaimStatus.SETTLEMENT_IN_PROGRESS);
        assertThat(claim.settlement().orElseThrow().failureReason()).isEqualTo("ACCOUNT_CLOSED");
    }

    @Test
    void unknownClaimIsReportedNotThrown() {
        PaymentResult orphan = new PaymentResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentResult.Status.PAID, "PAY", null, 1, "SGD", NOW);

        assertThat(record.record(orphan)).isEqualTo(RecordPaymentResult.Outcome.UNKNOWN_CLAIM);
    }

    private static PaymentResult result(Claim claim, UUID instructionId, PaymentResult.Status status) {
        return new PaymentResult(instructionId, SETTLEMENT_ID, claim.id().value(), status,
                status == PaymentResult.Status.PAID ? "PAY-1" : null,
                status == PaymentResult.Status.FAILED ? "ACCOUNT_CLOSED" : null, 420_000, "SGD", NOW);
    }
}
