package com.claims.platform.insights.application;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.insights.domain.ClaimSummary;

import java.util.Optional;

/**
 * Applies one claims event to the read model. Events of a claim arrive in order (Kafka key = claimId), and
 * duplicates are filtered by the caller (processed_event), so this is a straight fold.
 */
public class ProjectClaimEvent {
    public enum Outcome { CREATED, UPDATED, IGNORED_UNKNOWN_CLAIM }

    private final ClaimSummaryStore store;

    public ProjectClaimEvent(ClaimSummaryStore store) {
        this.store = store;
    }

    public Outcome apply(EventEnvelope event) {
        if (EventTypes.CLAIM_SUBMITTED.equals(event.type())) {
            if (store.find(event.claimId()).isEmpty()) {
                store.save(ClaimSummary.fromSubmitted(event));
                return Outcome.CREATED;
            }
            return Outcome.UPDATED;
        }
        Optional<ClaimSummary> summary = store.find(event.claimId());
        if (summary.isEmpty()) {
            // only possible if the read model was created after the claim (replay from the start to rebuild)
            return Outcome.IGNORED_UNKNOWN_CLAIM;
        }
        summary.get().apply(event);
        store.save(summary.get());
        return Outcome.UPDATED;
    }
}
