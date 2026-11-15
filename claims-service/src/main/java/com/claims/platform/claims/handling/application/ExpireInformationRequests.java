package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.ClaimWorkQueries;
import com.claims.platform.claims.core.application.ClaimWorkQueries.DueInformationRequest;
import com.claims.platform.claims.core.application.UnitOfWork;
import com.claims.platform.claims.core.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;

/** Info-request expiry job (daily): overdue requests go back to the handler (InformationRequestExpired). */
public class ExpireInformationRequests {
    private static final Logger log = LoggerFactory.getLogger(ExpireInformationRequests.class);
    private static final int BATCH = 500;

    private final ClaimWorkQueries work;
    private final ClaimRepository claims;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ExpireInformationRequests(ClaimWorkQueries work, ClaimRepository claims, UnitOfWork unitOfWork, Clock clock) {
        this.work = work;
        this.claims = claims;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** @return number of requests expired */
    public int runOnce() {
        Instant now = clock.instant();
        int expired = 0;
        for (DueInformationRequest due : work.dueInformationRequests(now, BATCH)) {
            try {
                unitOfWork.inTransaction(() -> {
                    var claim = claims.findById(due.claimId()).orElseThrow();
                    claim.expireInformationRequest(due.requestId(), now);
                    claims.save(claim);
                    return null;
                });
                expired++;
            } catch (DomainException ex) {
                // answered or withdrawn between the query and now: nothing to do
                log.info("Skipped expiring request {} on claim {}: {}", due.requestId(), due.claimId(), ex.code());
            }
        }
        return expired;
    }
}
