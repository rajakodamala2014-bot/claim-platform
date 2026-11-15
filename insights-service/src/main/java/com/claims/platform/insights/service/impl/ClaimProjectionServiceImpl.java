package com.claims.platform.insights.service.impl;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.insights.entity.ClaimSummary;
import com.claims.platform.insights.repository.ClaimSummaryRepository;
import com.claims.platform.insights.service.ClaimProjectionService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ClaimProjectionServiceImpl implements ClaimProjectionService {
    private final ClaimSummaryRepository summaries;

    public ClaimProjectionServiceImpl(ClaimSummaryRepository summaries) {
        this.summaries = summaries;
    }

    @Override
    public ProjectionOutcome projectClaimEvent(EventEnvelope event) {
        Optional<ClaimSummary> summary = summaries.findByClaimId(event.claimId());
        if (EventTypes.CLAIM_SUBMITTED.equals(event.type())) {
            if (summary.isEmpty()) {
                summaries.save(ClaimSummary.fromSubmitted(event));
                return ProjectionOutcome.CREATED;
            }
            return ProjectionOutcome.UPDATED;
        }
        if (summary.isEmpty()) {
            // only possible if the read model was created after the claim (replay from the start to rebuild)
            return ProjectionOutcome.IGNORED_UNKNOWN_CLAIM;
        }
        summary.get().apply(event);
        summaries.save(summary.get());
        return ProjectionOutcome.UPDATED;
    }
}
