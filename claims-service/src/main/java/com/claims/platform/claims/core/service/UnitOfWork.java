package com.claims.platform.claims.core.service;

import java.util.function.Supplier;

/**
 * Runs work in one database transaction. Use cases call remote systems (Policy system) <em>before</em>
 * opening a transaction, never inside one.
 */
public interface UnitOfWork {
    <T> T inTransaction(Supplier<T> work);
}
