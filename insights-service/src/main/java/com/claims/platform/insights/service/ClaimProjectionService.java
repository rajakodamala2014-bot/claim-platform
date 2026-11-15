package com.claims.platform.insights.service;

import com.claims.platform.common.event.EventEnvelope;

/**
 * Applies one claims event to the read model. Events of a claim arrive in order (Kafka key = claimId), and
 * duplicates are filtered by the caller (processed_event), so this is a straight fold.
 */
public interface ClaimProjectionService {
    ProjectionOutcome projectClaimEvent(EventEnvelope event);

    enum ProjectionOutcome { CREATED, UPDATED, IGNORED_UNKNOWN_CLAIM }
}
