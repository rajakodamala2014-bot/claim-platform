package com.claims.platform.claims.core.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ValueObjectsTest {

    @Test
    void claimNumberFormat() {
        assertThat(ClaimNumber.of(MarketCode.of("SG"), LineOfBusiness.MOTOR, 2026, 123))
                .hasToString("SG-MTR-2026-000123");
        assertThat(ClaimNumber.of(MarketCode.of("th"), LineOfBusiness.PROPERTY, 2026, 999_999))
                .hasToString("TH-PRP-2026-999999");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 1_000_000})
    void claimSequenceMustFitSixDigits(long sequence) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClaimNumber.of(MarketCode.of("SG"), LineOfBusiness.MOTOR, 2026, sequence));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SG-MTR-26-000123", "SG-CAR-2026-000123", "sg-MTR-2026-000123", "SG-MTR-2026-123"})
    void malformedClaimNumbersAreRejected(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClaimNumber(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SGP", "S", "1G", ""})
    void marketCodeIsTwoLetters(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> MarketCode.of(value));
    }

    @Test
    void marketCodeIsNormalised() {
        assertThat(MarketCode.of(" id ")).isEqualTo(MarketCode.of("ID"));
    }

    @Test
    void claimEventDropsNullDataAndIsImmutable() {
        var data = new java.util.HashMap<String, Object>();
        data.put("a", 1);
        data.put("b", null);
        ClaimEvent event = new ClaimEvent(java.util.UUID.randomUUID(), "ClaimSubmitted", ClaimEvent.Visibility.CLAIMANT,
                "x", Role.CUSTOMER, java.time.Instant.now(), data);

        assertThat(event.data()).containsOnlyKeys("a").isUnmodifiable();
    }
}
