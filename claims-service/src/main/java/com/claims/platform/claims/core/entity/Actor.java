package com.claims.platform.claims.core.entity;

import java.util.Objects;

/** Who performs a command. Recorded on every claim event for the audit trail. */
public record Actor(String id, Role role) {
    public static final Actor SYSTEM = new Actor("system", Role.SYSTEM);

    public Actor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
    }

    public static Actor customer(String id) {
        return new Actor(id, Role.CUSTOMER);
    }

    public static Actor adjuster(String id) {
        return new Actor(id, Role.CLAIMS_ADJUSTER);
    }

    /** Same person as {@link #customer(String)}. */
    public static Actor claimant(String id) {
        return customer(id);
    }

    /** Same person as {@link #adjuster(String)}. */
    public static Actor handler(String id) {
        return adjuster(id);
    }

    /** Same person as {@link #adjuster(String)}. */
    public static Actor teamLead(String id) {
        return adjuster(id);
    }

    /** Same person as {@link #adjuster(String)}. */
    public static Actor manager(String id) {
        return adjuster(id);
    }
}
