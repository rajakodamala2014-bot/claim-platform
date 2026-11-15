package com.claims.platform.claims.core.domain;

public enum Role {
    CLAIMANT,
    HANDLER,
    TEAM_LEAD,
    MANAGER,
    /** Background jobs and Kafka consumers acting on behalf of the platform. */
    SYSTEM;

    public boolean isStaff() {
        return this == HANDLER || this == TEAM_LEAD || this == MANAGER;
    }

    public boolean isSupervisor() {
        return this == TEAM_LEAD || this == MANAGER;
    }
}
