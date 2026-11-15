package com.claims.platform.claims.core.domain;

import java.util.Objects;

/** Who performs a command. Recorded on every claim event for the audit trail. */
public record Actor(String id, Role role) {
    public static final Actor SYSTEM = new Actor("system", Role.SYSTEM);

    public Actor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
    }

    public static Actor claimant(String id) {
        return new Actor(id, Role.CLAIMANT);
    }

    public static Actor handler(String id) {
        return new Actor(id, Role.HANDLER);
    }

    public static Actor teamLead(String id) {
        return new Actor(id, Role.TEAM_LEAD);
    }

    public static Actor manager(String id) {
        return new Actor(id, Role.MANAGER);
    }
}
