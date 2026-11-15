package com.claims.platform.architecture.fixtures.baddomain.orders.domain;

import org.assertj.core.api.Assertions;

/** Violation: domain depends on a third-party library. */
public class LeakyOrder {
    public void check() {
        Assertions.assertThat(1).isPositive();
    }
}
