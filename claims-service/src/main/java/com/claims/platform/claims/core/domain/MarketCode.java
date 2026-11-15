package com.claims.platform.claims.core.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** ISO-3166 alpha-2 market (SG, HK, MY, TH, ID, AU...). Markets are configuration, not an enum. */
public record MarketCode(String value) {
    private static final Pattern ALPHA2 = Pattern.compile("[A-Z]{2}");

    public MarketCode {
        Objects.requireNonNull(value, "value");
        value = value.trim().toUpperCase();
        if (!ALPHA2.matcher(value).matches()) {
            throw new IllegalArgumentException("Market code must be two letters: " + value);
        }
    }

    public static MarketCode of(String value) {
        return new MarketCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
