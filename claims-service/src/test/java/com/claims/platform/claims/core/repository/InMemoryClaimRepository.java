package com.claims.platform.claims.core.repository;

import com.claims.platform.claims.core.service.UnitOfWork;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimEvent;
import com.claims.platform.claims.core.entity.ClaimId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Test double: keeps claims in memory and records every event that would have been written. */
public class InMemoryClaimRepository implements ClaimRepository {
    public final Map<ClaimId, Claim> byId = new HashMap<>();
    public final List<ClaimEvent> written = new ArrayList<>();
    private long sequence;

    public Claim add(Claim claim) {
        save(claim);
        return claim;
    }

    @Override
    public Optional<Claim> findById(ClaimId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public void save(Claim claim) {
        written.addAll(claim.pullPendingEvents());
        claim.markSaved(claim.version() + 1);
        byId.put(claim.id(), claim);
    }

    @Override
    public long nextClaimSequence() {
        return ++sequence;
    }

    /** Runs work directly (no transaction), for use-case unit tests. */
    public static UnitOfWork directUnitOfWork() {
        return new UnitOfWork() {
            @Override
            public <T> T inTransaction(Supplier<T> work) {
                return work.get();
            }
        };
    }
}
