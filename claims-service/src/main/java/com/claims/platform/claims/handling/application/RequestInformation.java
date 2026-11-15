package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.platform.application.MarketConfigs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** S7: ask the customer for documents/answers. Due date defaults to the market setting (info_request_due_days). */
public class RequestInformation {
    private final StaffClaims staffClaims;
    private final MarketConfigs markets;
    private final Clock clock;

    public RequestInformation(StaffClaims staffClaims, MarketConfigs markets, Clock clock) {
        this.staffClaims = staffClaims;
        this.markets = markets;
        this.clock = clock;
    }

    public Outcome<UUID> request(ClaimId claimId, StaffContext staff, long expectedVersion, List<String> items, Instant dueAt) {
        UUID requestId = UUID.randomUUID();
        return staffClaims.change(claimId, staff, expectedVersion, claim -> {
            Instant now = clock.instant();
            Instant due = dueAt != null ? dueAt
                    : now.plus(Duration.ofDays(markets.requireEnabled(claim.market()).infoRequestDueDays()));
            claim.requestInformation(requestId, items, due, staff.actor(), now);
            return requestId;
        });
    }
}
