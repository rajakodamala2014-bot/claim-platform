package com.claims.platform.claims.core.domain;

import java.util.Objects;
import java.util.UUID;

public record ClaimId(UUID value) {
    public ClaimId {
        Objects.requireNonNull(value, "value");
    }

    public static ClaimId newId() {
        return new ClaimId(UUID.randomUUID());
    }

    public static ClaimId of(String value) {
        return new ClaimId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
