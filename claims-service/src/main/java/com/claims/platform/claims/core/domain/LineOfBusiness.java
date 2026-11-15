package com.claims.platform.claims.core.domain;

public enum LineOfBusiness {
    MOTOR("MTR"),
    PROPERTY("PRP");

    private final String code;

    LineOfBusiness(String code) {
        this.code = code;
    }

    /** Three-letter code used in claim numbers, e.g. SG-<b>MTR</b>-2026-000123. */
    public String code() {
        return code;
    }
}
