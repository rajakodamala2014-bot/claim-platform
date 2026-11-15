package com.claims.platform.notification.domain;

/** Who a notification is for. HANDLER covers the assigned handler, the proposer of a decision, etc. */
public enum Audience {
    CUSTOMER,
    HANDLER,
    TEAM_LEAD
}
