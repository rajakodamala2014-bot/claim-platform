package com.claims.platform.paymentsim;

import com.claims.platform.common.payments.PaymentResult;
import com.claims.platform.common.payments.SettlementRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FakeBankTest {
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private final FakeBank bank = new FakeBank(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void paysValidInstruction() {
        PaymentResult result = bank.process(request(UUID.randomUUID(), "123456789", 420_000));

        assertThat(result.status()).isEqualTo(PaymentResult.Status.PAID);
        assertThat(result.paymentRef()).startsWith("PAY-");
        assertThat(result.failureReason()).isNull();
        assertThat(result.amountMinor()).isEqualTo(420_000);
        assertThat(result.processedAt()).isEqualTo(NOW);
    }

    @Test
    void failsClosedAccount() {
        PaymentResult result = bank.process(request(UUID.randomUUID(), "12340000", 420_000));

        assertThat(result.status()).isEqualTo(PaymentResult.Status.FAILED);
        assertThat(result.failureReason()).isEqualTo("ACCOUNT_CLOSED");
        assertThat(result.paymentRef()).isNull();
    }

    @Test
    void failsNonPositiveAmount() {
        assertThat(bank.process(request(UUID.randomUUID(), "123456789", 0)).failureReason()).isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void redeliveredInstructionGetsTheSameAnswer() {
        UUID instructionId = UUID.randomUUID();

        PaymentResult first = bank.process(request(instructionId, "123456789", 100));
        PaymentResult again = bank.process(request(instructionId, "123456789", 100));

        assertThat(again).isEqualTo(first);
    }

    private static SettlementRequest request(UUID instructionId, String account, long amountMinor) {
        return new SettlementRequest(instructionId, UUID.randomUUID(), UUID.randomUUID(), "SG-MTR-2026-000123",
                "Priya Nair", "DBSSSGSG", account, amountMinor, "SGD", NOW);
    }
}
