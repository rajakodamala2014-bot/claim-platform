package com.claims.platform.claims.intake.entity;

import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.core.exception.ValidationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** A policy as returned by the Policy system, captured on the claim at FNOL. */
public record PolicySnapshot(
        String policyNumber,
        String holderId,
        String holderName,
        MarketCode market,
        LineOfBusiness lineOfBusiness,
        String product,
        String status,
        LocalDate coverageStart,
        LocalDate coverageEnd,
        String currency,
        Long sumInsuredMinor,
        Long excessMinor,
        List<String> coveredPerils
) {
    public PolicySnapshot {
        Objects.requireNonNull(policyNumber, "policyNumber");
        Objects.requireNonNull(holderId, "holderId");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(lineOfBusiness, "lineOfBusiness");
        Objects.requireNonNull(coverageStart, "coverageStart");
        Objects.requireNonNull(coverageEnd, "coverageEnd");
        coveredPerils = coveredPerils == null ? List.of() : List.copyOf(coveredPerils);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isInForceOn(LocalDate date) {
        return isActive() && !date.isBefore(coverageStart) && !date.isAfter(coverageEnd);
    }

    /**
     * Checks we can make from the snapshot alone, before calling the Policy system's cover check:
     * the claim is for this policy's line of business, and the policy was in force on the incident date.
     */
    public void requireClaimable(LineOfBusiness claimedLine, LocalDate incidentDate) {
        if (claimedLine != lineOfBusiness) {
            throw ValidationException.of("lineOfBusiness",
                    "policy " + policyNumber + " is a " + lineOfBusiness + " policy");
        }
        if (!isInForceOn(incidentDate)) {
            throw new DomainException(ErrorCode.POLICY_NOT_IN_FORCE,
                    "Policy " + policyNumber + " was not in force on " + incidentDate);
        }
    }
}
