package com.claims.platform.claims.intake.service;

import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.Channel;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.intake.entity.IncidentDetails;
import com.claims.platform.claims.intake.dto.response.SubmitClaimResponse;
import com.claims.platform.common.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** First notification of loss and the customer's own changes to a claim (C3, C9, C10, S1). */
public interface ClaimIntakeService {
    /**
     * @param claimantId   whose claim it is: the caller for C3, the customer for S1
     * @param homeMarket   market to use if the Policy system can't tell us (claimant's market from the token)
     */
    record SubmitClaimCommand(
            String claimantId,
            String claimantName,
            MarketCode homeMarket,
            Channel channel,
            String policyNumber,
            LineOfBusiness lineOfBusiness,
            Instant incidentAt,
            IncidentDetails incident,
            Money claimedAmount,
            List<UUID> uploadIds,
            String contactPhone,
            String contactEmail
    ) {
        public SubmitClaimCommand {
            uploadIds = uploadIds == null ? List.of() : List.copyOf(uploadIds);
        }
    }

    /**
     * C3 (customer) / S1 (staff on the customer's behalf): FNOL.
     * <ol>
     *     <li>Idempotency-Key replay check.</li>
     *     <li>Validate incident details per line of business.</li>
     *     <li>Outside any transaction: fetch the policy and run the cover check (2 s timeouts). If the Policy system
     *     is unavailable the claim is still created, with cover pending (retried by a job).</li>
     *     <li>One transaction: claim + incident + parties + policy snapshot + documents + events/outbox +
     *     idempotency record.</li>
     * </ol>
     */
    SubmitClaimResponse submitClaim(Actor actor, UUID idempotencyKey, SubmitClaimCommand command);

    /**
     * @param allowedMarkets for staff (S1): the markets the caller may work in; null for a customer (C3)
     */
    SubmitClaimResponse registerClaimForCustomer(Actor actor, UUID idempotencyKey, SubmitClaimCommand command,
            Set<MarketCode> allowedMarkets);

    /** C9: the customer withdraws before a decision. */
    String withdrawClaim(ClaimId claimId, Actor claimant, String reason);

    /** C10: the customer appeals a rejection, which reopens the claim. */
    String appealClaim(ClaimId claimId, Actor claimant, String reason);
}
