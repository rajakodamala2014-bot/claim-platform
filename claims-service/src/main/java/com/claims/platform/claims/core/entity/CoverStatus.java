package com.claims.platform.claims.core.entity;

public enum CoverStatus {
    /** Policy system not reached yet (timeout/outage); the retry job will check again. FNOL is never blocked. */
    PENDING,
    CONFIRMED,
    /** Policy system said not covered. Cover is still confirmed on submit, so this flag is historical. */
    DECLINED
}
