package com.claims.platform.claims.core.application;

import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;

import java.util.function.Function;

/**
 * Loading and changing claims on behalf of staff. Every staff command goes through {@link #change}:
 * market scope check -> If-Match version check (412) -> domain command -> save (optimistic lock) in one
 * transaction. Market is checked before the version so a foreign market's claim reveals nothing.
 */
public class StaffClaims {
    private final ClaimRepository claims;
    private final UnitOfWork unitOfWork;

    public StaffClaims(ClaimRepository claims, UnitOfWork unitOfWork) {
        this.claims = claims;
        this.unitOfWork = unitOfWork;
    }

    public Claim loadVisible(ClaimId claimId, StaffContext staff) {
        Claim claim = claims.findById(claimId)
                .orElseThrow(() -> new DomainException(ErrorCode.CLAIM_NOT_FOUND, "Claim " + claimId + " not found"));
        staff.requireMarket(claim.market());
        return claim;
    }

    public <T> Outcome<T> change(ClaimId claimId, StaffContext staff, long expectedVersion, Function<Claim, T> command) {
        return unitOfWork.inTransaction(() -> {
            Claim claim = loadVisible(claimId, staff);
            if (claim.version() != expectedVersion) {
                throw new DomainException(ErrorCode.STALE_VERSION, "Claim " + claim.number() + " is at version "
                        + claim.version() + ", not " + expectedVersion + "; reload and try again");
            }
            T value = command.apply(claim);
            claims.save(claim);
            return new Outcome<>(value, claim.id(), claim.status().name(), claim.version());
        });
    }

    /** Result of a staff command; {@code version} becomes the new ETag. */
    public record Outcome<T>(T value, ClaimId claimId, String status, long version) {
    }
}
