package com.claims.platform.claims.core.entity;

/** Who is signed in. Customers report claims. Claims adjusters assess, decide, and approve them. */
public enum Role {
    CUSTOMER,
    CLAIMS_ADJUSTER,
    /** Background jobs and Kafka consumers acting on behalf of the platform. */
    SYSTEM;

    public boolean isStaff() {
        return this == CLAIMS_ADJUSTER;
    }

    /** Every claims adjuster may approve another adjuster's decision and open the team screens. */
    public boolean isSupervisor() {
        return this == CLAIMS_ADJUSTER;
    }
}
