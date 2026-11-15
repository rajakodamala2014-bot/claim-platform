package com.claims.platform.claims.platform.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency-Key replay store, scoped per caller. {@link #save} runs inside the business transaction, so the
 * stored response and the change it describes commit (or roll back) together.
 */
public interface IdempotencyStore {

    /** Stable fingerprint (hash) of a request, used to detect a key reused with a different body. */
    String fingerprint(Object request);

    <T> Optional<Stored<T>> find(String callerId, UUID key, Class<T> responseType);

    /** @throws KeyAlreadyUsedException when a concurrent request committed the same key first */
    void save(String callerId, UUID key, String fingerprint, int status, Object response);

    record Stored<T>(String fingerprint, int status, T response) {
    }

    class KeyAlreadyUsedException extends RuntimeException {
        public KeyAlreadyUsedException(UUID key) {
            super("Idempotency key " + key + " already used");
        }
    }
}
