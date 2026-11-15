package com.claims.platform.common.money;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Amount in the currency's minor unit (cents, sen...) plus an ISO-4217 code. Integer arithmetic only, so
 * reserve ledgers and authority checks never suffer rounding. The number of minor digits comes from
 * {@link Currency#getDefaultFractionDigits()} (SGD/HKD/MYR/THB/IDR/AUD = 2).
 */
public record Money(long amountMinor, String currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
        currency = currency.trim().toUpperCase();
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown ISO currency " + currency, ex);
        }
    }

    public static Money zero(String currency) {
        return new Money(0, currency);
    }

    public static Money ofMinor(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    /** Parses a major-unit amount such as {@code "4200.50"}; more decimals than the currency allows is an error. */
    public static Money of(String majorAmount, String currency) {
        Objects.requireNonNull(majorAmount, "majorAmount");
        int digits = fractionDigits(currency);
        try {
            BigDecimal minor = new BigDecimal(majorAmount.trim()).movePointRight(digits);
            return new Money(minor.longValueExact(), currency);
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid amount " + majorAmount + " for " + currency, ex);
        }
    }

    public BigDecimal toMajor() {
        return BigDecimal.valueOf(amountMinor, fractionDigits(currency));
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amountMinor), currency);
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amountMinor, other.amountMinor);
    }

    public void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return currency + " " + toMajor().toPlainString();
    }

    private static int fractionDigits(String currency) {
        return Math.max(0, Currency.getInstance(currency.trim().toUpperCase()).getDefaultFractionDigits());
    }
}
