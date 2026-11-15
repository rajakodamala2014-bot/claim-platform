package com.claims.platform.common.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({
            "4200,     SGD, 420000",
            "4200.5,   SGD, 420050",
            "0.01,     HKD, 1",
            "1500000,  IDR, 150000000",
            "99.99,    AUD, 9999",
            "1000,     JPY, 1000",
    })
    void parsesMajorUnitsUsingTheCurrencyMinorDigits(String major, String currency, long expectedMinor) {
        assertThat(Money.of(major, currency).amountMinor()).isEqualTo(expectedMinor);
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyAllows() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1.001", "SGD"));
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1.5", "JPY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"XXX1", "SG", "DOLLAR"})
    void rejectsUnknownCurrency(String currency) {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.zero(currency));
    }

    @Test
    void normalisesCurrencyCode() {
        assertThat(Money.ofMinor(1, " sgd ")).isEqualTo(Money.ofMinor(1, "SGD"));
    }

    @Test
    void arithmeticAndComparison() {
        Money five = Money.of("5000", "SGD");
        Money three = Money.of("3000", "SGD");

        assertThat(five.plus(three)).isEqualTo(Money.of("8000", "SGD"));
        assertThat(three.minus(five)).isEqualTo(Money.of("-2000", "SGD"));
        assertThat(five.negate().isNegative()).isTrue();
        assertThat(five.isGreaterThan(three)).isTrue();
        assertThat(three.isGreaterThan(five)).isFalse();
        assertThat(Money.zero("SGD").isZero()).isTrue();
        assertThat(five.toMajor()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(five).hasToString("SGD 5000.00");
    }

    @Test
    void neverMixesCurrencies() {
        Money sgd = Money.of("1", "SGD");
        Money hkd = Money.of("1", "HKD");

        assertThatThrownBy(() -> sgd.plus(hkd)).isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Currency mismatch: expected SGD but was HKD");
        assertThatThrownBy(() -> sgd.minus(hkd)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> sgd.compareTo(hkd)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void overflowIsAnErrorNotAWrapAround() {
        Money max = Money.ofMinor(Long.MAX_VALUE, "SGD");

        assertThatThrownBy(() -> max.plus(Money.ofMinor(1, "SGD"))).isInstanceOf(ArithmeticException.class);
    }
}
