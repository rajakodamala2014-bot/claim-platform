package com.claims.platform.claims.core.service;

import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;
import java.util.function.Function;

/** Loading and changing claims on behalf of staff: market scope, If-Match version check, domain command and save in one transaction. */
public interface StaffClaimCommandService {
    /** Result of a staff command; {@code version} becomes the new ETag. */
    record Outcome<T>(T value, ClaimId claimId, String status, long version) {
    }

    /**
     * Loading and changing claims on behalf of staff. Every staff command goes through {@link #change}:
     * market scope check -> If-Match version check (412) -> domain command -> save (optimistic lock) in one
     * transaction. Market is checked before the version so a foreign market's claim reveals nothing.
     */
    Claim loadVisibleClaim(ClaimId claimId, StaffContext staff);

    /**
     * Loading and changing claims on behalf of staff. Every staff command goes through {@link #change}:
     * market scope check -> If-Match version check (412) -> domain command -> save (optimistic lock) in one
     * transaction. Market is checked before the version so a foreign market's claim reveals nothing.
     */
    <T> Outcome<T> applyChange(ClaimId claimId, StaffContext staff, long expectedVersion, Function<Claim, T> command);
}
