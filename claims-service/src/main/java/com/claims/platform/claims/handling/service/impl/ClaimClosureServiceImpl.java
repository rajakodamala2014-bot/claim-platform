package com.claims.platform.claims.handling.service.impl;

import com.claims.platform.claims.core.repository.ClaimRepository;
import com.claims.platform.claims.core.repository.ClaimWorkRepository;
import com.claims.platform.claims.core.service.UnitOfWork;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.handling.service.ClaimClosureService;
import com.claims.platform.claims.platform.repository.MarketConfigRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClaimClosureServiceImpl implements ClaimClosureService {
    private static final Logger log = LoggerFactory.getLogger(ClaimClosureServiceImpl.class);
    private static final int BATCH = 500;

    private final ClaimWorkRepository work;
    private final ClaimRepository claims;
    private final MarketConfigRepository markets;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ClaimClosureServiceImpl(ClaimWorkRepository work, ClaimRepository claims, MarketConfigRepository markets,
                                   UnitOfWork unitOfWork, Clock clock) {
        this.work = work;
        this.claims = claims;
        this.markets = markets;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public int closeClaimsAfterCoolingOff() {
        Instant now = clock.instant();
        int closed = 0;
        for (ClaimId id : work.findClosableClaims(now, BATCH)) {
            try {
                Boolean done = unitOfWork.inTransaction(() -> {
                    var claim = claims.findById(id).orElseThrow();
                    if (!claim.isDueForClosure(now, markets.requireEnabled(claim.market()).coolingOff())) {
                        return false; // appealed meanwhile
                    }
                    claim.close(now);
                    claims.save(claim);
                    return true;
                });
                closed += Boolean.TRUE.equals(done) ? 1 : 0;
            } catch (DomainException ex) {
                log.info("Skipped closing claim {}: {}", id, ex.code());
            }
        }
        return closed;
    }
}
