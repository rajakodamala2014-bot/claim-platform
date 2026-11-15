package com.claims.platform.insights.application;

import com.claims.platform.insights.domain.ClaimSummary;

import java.util.Optional;
import java.util.UUID;

public interface ClaimSummaryStore {
    Optional<ClaimSummary> find(UUID claimId);

    void save(ClaimSummary summary);
}
