package com.claims.platform.claims.core.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Human-facing claim reference: {@code <MARKET>-<LOB>-<YEAR>-<6-digit sequence>}, e.g. SG-MTR-2026-000123. */
public record ClaimNumber(String value) {
    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}-(MTR|PRP)-\\d{4}-\\d{6}");

    public ClaimNumber {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid claim number " + value);
        }
    }

    public static ClaimNumber of(MarketCode market, LineOfBusiness lob, int year, long sequence) {
        if (sequence < 1 || sequence > 999_999) {
            throw new IllegalArgumentException("Claim sequence out of range: " + sequence);
        }
        return new ClaimNumber("%s-%s-%04d-%06d".formatted(market.value(), lob.code(), year, sequence));
    }

    @Override
    public String toString() {
        return value;
    }
}
