package com.claims.platform.claims.intake.entity;

/** Answer of the Policy system's cover check. UNAVAILABLE never blocks FNOL: the claim stays cover-pending. */
public sealed interface CoverCheck {
    record Covered() implements CoverCheck {
    }

    record NotCovered(String reason) implements CoverCheck {
    }

    record Unavailable(String cause) implements CoverCheck {
    }
}
