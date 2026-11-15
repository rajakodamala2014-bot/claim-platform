package com.claims.platform.insights.entity;

import java.time.Duration;
import java.time.Instant;

/** How a claim stands against its SLA deadline. */
public enum SlaState {
    /** Clock stopped: waiting on the customer, or a decision has been reached. */
    PAUSED,
    ON_TRACK,
    /** Due within the at-risk window. */
    AT_RISK,
    BREACHED;

    public static SlaState of(Instant dueAt, boolean running, Instant now, Duration atRiskWindow) {
        if (!running) {
            return PAUSED;
        }
        if (!dueAt.isAfter(now)) {
            return BREACHED;
        }
        return dueAt.isAfter(now.plus(atRiskWindow)) ? ON_TRACK : AT_RISK;
    }
}
