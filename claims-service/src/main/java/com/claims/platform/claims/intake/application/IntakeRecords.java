package com.claims.platform.claims.intake.application;

import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.intake.domain.IncidentDetails;
import com.claims.platform.claims.intake.domain.Party;
import com.claims.platform.claims.intake.domain.PolicySnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Intake-owned records stored next to the claim (intake schema). Runs in the caller's transaction. */
public interface IntakeRecords {

    void saveIncident(ClaimId claimId, MarketCode market, Instant occurredAt, IncidentDetails incident);

    void saveParties(ClaimId claimId, MarketCode market, List<Party> parties);

    void savePolicySnapshot(ClaimId claimId, PolicySnapshot policy, Instant capturedAt);

    Optional<IncidentDetails> findIncident(ClaimId claimId);

    Optional<PolicySnapshot> findPolicySnapshot(ClaimId claimId);

    List<Party> findParties(ClaimId claimId);
}
