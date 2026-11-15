package com.claims.platform.claims.core.entity;

public enum CoverStatus {
    /** Policy system not reached yet (timeout/outage); the retry job will check again. FNOL is never blocked. */
    PENDING,
    CONFIRMED,
    /** Policy system says not covered; the claim is triaged and flagged so a handler reviews it. */
    DECLINED
}
