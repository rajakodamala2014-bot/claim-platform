package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.ClaimWorkQueries;
import com.claims.platform.claims.core.application.UnitOfWork;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.platform.application.MarketConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;

/** Auto-close job (daily): SETTLED / REJECTED / WITHDRAWN claims past the market's cooling-off -> CLOSED. */
public class CloseCoolingOffClaims {
    private static final Logger log = LoggerFactory.getLogger(CloseCoolingOffClaims.class);
    private static final int BATCH = 500;

    private final ClaimWorkQueries work;
    private final ClaimRepository claims;
    private final MarketConfigs markets;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CloseCoolingOffClaims(ClaimWorkQueries work, ClaimRepository claims, MarketConfigs markets,
                                 UnitOfWork unitOfWork, Clock clock) {
        this.work = work;
        this.claims = claims;
        this.markets = markets;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** @return number of claims closed */
    public int runOnce() {
        Instant now = clock.instant();
        int closed = 0;
        for (ClaimId id : work.closableClaims(now, BATCH)) {
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
