package com.claims.platform.claims.settlement.service.impl;

import com.claims.platform.claims.settlement.service.PaymentResultService;
import com.claims.platform.claims.core.repository.InMemoryClaimRepository;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimFixture;
import com.claims.platform.claims.core.entity.ClaimStatus;
import com.claims.platform.common.payments.PaymentResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.claims.platform.claims.core.entity.ClaimFixture.INSTRUCTION_ID;
import static com.claims.platform.claims.core.entity.ClaimFixture.SETTLEMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentResultServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-14T05:00:00Z");
    private final InMemoryClaimRepository repository = new InMemoryClaimRepository();
    private final PaymentResultService paymentResultService = new PaymentResultServiceImpl(repository,
            InMemoryClaimRepository.directUnitOfWork(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void paidSettlesOnceAndDuplicatesAreIgnored() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.SETTLEMENT_IN_PROGRESS));
        PaymentResult paid = result(claim, INSTRUCTION_ID, PaymentResult.Status.PAID);

        assertThat(paymentResultService.recordPaymentResult(paid)).isEqualTo(PaymentResultService.PaymentResultOutcome.APPLIED);
        assertThat(paymentResultService.recordPaymentResult(paid)).isEqualTo(PaymentResultService.PaymentResultOutcome.IGNORED_DUPLICATE);
        assertThat(claim.status()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(claim.version()).isEqualTo(2);
    }

    @Test
    void failedKeepsTheClaimInProgressAndStaleAttemptsAreIgnored() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.SETTLEMENT_IN_PROGRESS));

        assertThat(paymentResultService.recordPaymentResult(result(claim, UUID.randomUUID(), PaymentResult.Status.PAID)))
                .isEqualTo(PaymentResultService.PaymentResultOutcome.IGNORED_DUPLICATE);
        assertThat(paymentResultService.recordPaymentResult(result(claim, INSTRUCTION_ID, PaymentResult.Status.FAILED)))
                .isEqualTo(PaymentResultService.PaymentResultOutcome.APPLIED);
        assertThat(claim.status()).isEqualTo(ClaimStatus.SETTLEMENT_IN_PROGRESS);
        assertThat(claim.settlement().orElseThrow().failureReason()).isEqualTo("ACCOUNT_CLOSED");
    }

    @Test
    void unknownClaimIsReportedNotThrown() {
        PaymentResult orphan = new PaymentResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentResult.Status.PAID, "PAY", null, 1, "SGD", NOW);

        assertThat(paymentResultService.recordPaymentResult(orphan)).isEqualTo(PaymentResultService.PaymentResultOutcome.UNKNOWN_CLAIM);
    }

    private static PaymentResult result(Claim claim, UUID instructionId, PaymentResult.Status status) {
        return new PaymentResult(instructionId, SETTLEMENT_ID, claim.id().value(), status,
                status == PaymentResult.Status.PAID ? "PAY-1" : null,
                status == PaymentResult.Status.FAILED ? "ACCOUNT_CLOSED" : null, 420_000, "SGD", NOW);
    }
}
