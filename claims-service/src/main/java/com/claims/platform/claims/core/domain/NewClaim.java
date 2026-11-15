package com.claims.platform.claims.core.domain;

import com.claims.platform.common.money.Money;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Everything needed to register a claim (C3/S1). The application layer has already validated the incident
 * details per line of business and allocated the id and number.
 *
 * @param slaTarget       market SLA (from market_config), measured from submission, paused while awaiting info
 * @param claimedAmount   customer's estimate, optional
 */
public record NewClaim(
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
        Duration slaTarget
) {
    public NewClaim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(lineOfBusiness, "lineOfBusiness");
        Objects.requireNonNull(policyNumber, "policyNumber");
        Objects.requireNonNull(claimantId, "claimantId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(incidentDate, "incidentDate");
        Objects.requireNonNull(slaTarget, "slaTarget");
        if (!number.value().startsWith(market.value() + "-" + lineOfBusiness.code() + "-")) {
            throw new IllegalArgumentException("Claim number " + number + " does not match " + market + "/" + lineOfBusiness);
        }
        if (claimedAmount != null && !claimedAmount.currency().equals(Money.zero(currency).currency())) {
            throw new DomainException(ErrorCode.CURRENCY_MISMATCH, "Claimed amount must be in " + currency);
        }
    }
}
