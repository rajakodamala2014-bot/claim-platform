package com.claims.platform.architecture.fixtures.badlayer.orders.domain;

import com.claims.platform.architecture.fixtures.badlayer.orders.application.UseCase;

/** Violation: domain depends on the application layer. */
public class Backwards {
    private UseCase useCase;
}
