package com.claims.platform.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(
        String eventId,
        String type,
        int version,
        Instant occurredAt,
        String market,
        UUID claimId,
        String correlationId,
        Map<String, Object> data
) {
}
