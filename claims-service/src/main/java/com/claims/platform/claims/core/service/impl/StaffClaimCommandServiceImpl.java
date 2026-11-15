package com.claims.platform.claims.core.service.impl;

import com.claims.platform.claims.core.repository.ClaimRepository;
import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.service.UnitOfWork;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.service.StaffClaimCommandService;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class StaffClaimCommandServiceImpl implements StaffClaimCommandService {
    private final ClaimRepository claims;
    private final UnitOfWork unitOfWork;

    public StaffClaimCommandServiceImpl(ClaimRepository claims, UnitOfWork unitOfWork) {
        this.claims = claims;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public Claim loadVisibleClaim(ClaimId claimId, StaffContext staff) {
        Claim claim = claims.findById(claimId)
                .orElseThrow(() -> new DomainException(ErrorCode.CLAIM_NOT_FOUND, "Claim " + claimId + " not found"));
        staff.requireMarket(claim.market());
        return claim;
    }

    @Override
    public <T> Outcome<T> applyChange(ClaimId claimId, StaffContext staff, long expectedVersion, Function<Claim, T> command) {
        return unitOfWork.inTransaction(() -> {
            Claim claim = loadVisibleClaim(claimId, staff);
            if (claim.version() != expectedVersion) {
                throw new DomainException(ErrorCode.STALE_VERSION, "Claim " + claim.number() + " is at version "
                        + claim.version() + ", not " + expectedVersion + "; reload and try again");
            }
            T value = command.apply(claim);
            claims.save(claim);
            return new Outcome<>(value, claim.id(), claim.status().name(), claim.version());
        });
    }
}
