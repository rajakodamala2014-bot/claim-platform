package com.claims.platform.claims.core.domain;

import com.claims.platform.common.money.Money;

import java.time.Instant;
import java.util.List;

/**
 * Persisted form of a {@link Claim}, used only by the repository to rebuild the aggregate
 * ({@link Claim#restore}). Not a way to change a claim: no business code constructs one.
 */
public record ClaimState(
        ClaimId id,
        ClaimNumber number,
        MarketCode market,
        LineOfBusiness lineOfBusiness,
        String policyNumber,
        String claimantId,
        Channel channel,
        String currency,
        Instant incidentDate,
        Money claimedAmount,
        Instant submittedAt,
        ClaimStatus status,
        Instant statusChangedAt,
        CoverStatus coverStatus,
        String coverDeclineReason,
        String handlerId,
        String teamId,
        Instant slaDueAt,
        Instant slaPausedAt,
        Money approvedAmount,
        List<ReserveMovement> reserveMovements,
        List<InformationRequest> informationRequests,
        Assessment assessment,
        Decision decision,
        Settlement settlement,
        long version
) {
    public ClaimState {
        reserveMovements = List.copyOf(reserveMovements);
        informationRequests = List.copyOf(informationRequests);
    }
}
