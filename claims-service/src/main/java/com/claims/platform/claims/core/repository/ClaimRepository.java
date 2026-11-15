package com.claims.platform.claims.core.repository;

import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;

import java.util.Optional;

/** Loads and stores the whole Claim aggregate. */
public interface ClaimRepository {

    Optional<Claim> findById(ClaimId id);

    /**
     * Inserts or updates the claim and its children, and appends its pending events to the audit trail and the
     * outbox, all in the caller's transaction. Optimistic locking: if the claim changed since it was loaded,
     * throws {@code DomainException(CONCURRENT_UPDATE)}.
     */
    void save(Claim claim);

    /** Next value for claim numbers (gaps are fine; uniqueness is what matters). */
    long nextClaimSequence();
}
