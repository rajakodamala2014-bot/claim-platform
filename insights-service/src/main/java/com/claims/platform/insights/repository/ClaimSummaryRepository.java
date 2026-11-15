package com.claims.platform.insights.repository;

import com.claims.platform.insights.entity.ClaimSummary;

import java.util.Optional;
import java.util.UUID;

/** The claim_summary read model: one row per claim. */
public interface ClaimSummaryRepository {
    Optional<ClaimSummary> findByClaimId(UUID claimId);

    void save(ClaimSummary summary);
}
