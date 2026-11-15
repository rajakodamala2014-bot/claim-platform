package com.claims.platform.common.money;

/** Arithmetic or comparison across currencies; amounts are never converted implicitly. */
public class CurrencyMismatchException extends IllegalArgumentException {
    private final String expected;
    private final String actual;

    public CurrencyMismatchException(String expected, String actual) {
        super("Currency mismatch: expected " + expected + " but was " + actual);
        this.expected = expected;
        this.actual = actual;
    }

    public String expected() {
        return expected;
    }

    public String actual() {
        return actual;
    }
}
