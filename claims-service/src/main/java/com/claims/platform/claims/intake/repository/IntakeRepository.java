package com.claims.platform.claims.intake.repository;

import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.intake.entity.IncidentDetails;
import com.claims.platform.claims.intake.entity.Party;
import com.claims.platform.claims.intake.entity.PolicySnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Intake-owned records stored next to the claim (intake schema). Runs in the caller's transaction. */
public interface IntakeRepository {

    void saveIncident(ClaimId claimId, MarketCode market, Instant occurredAt, IncidentDetails incident);

    void saveParties(ClaimId claimId, MarketCode market, List<Party> parties);

    void savePolicySnapshot(ClaimId claimId, PolicySnapshot policy, Instant capturedAt);

    Optional<IncidentDetails> findIncident(ClaimId claimId);

    Optional<PolicySnapshot> findPolicySnapshot(ClaimId claimId);

    List<Party> findParties(ClaimId claimId);
}
