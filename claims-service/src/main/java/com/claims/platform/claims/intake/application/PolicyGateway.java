package com.claims.platform.claims.intake.application;

import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.intake.domain.CoverCheck;
import com.claims.platform.claims.intake.domain.PolicySnapshot;

import java.time.LocalDate;
import java.util.List;

/**
 * Port to the external Policy admin system (sync REST, 2 s timeout).
 * Implementations must never let the Policy system's outage turn into a failed FNOL.
 */
public interface PolicyGateway {

    /**
     * Active policies of a holder (cached for a few minutes).
     *
     * @throws com.claims.platform.claims.core.domain.DomainException POLICY_SYSTEM_UNAVAILABLE on timeout/outage
     */
    List<PolicySnapshot> policiesOf(String holderId);

    /** Never throws for availability problems: returns {@link CoverCheck.Unavailable} instead. */
    CoverCheck checkCover(String policyNumber, LineOfBusiness lineOfBusiness, LocalDate incidentDate, String peril);
}
