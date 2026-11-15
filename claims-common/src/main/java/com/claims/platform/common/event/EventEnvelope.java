package com.claims.platform.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Wire format for every message on {@code claims.events}. Shared by producers and consumers so the
 * contract lives in one place. {@code eventId} is the consumer de-duplication key; {@code claimId} is the
 * Kafka partition key.
 */
public record EventEnvelope(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        UUID claimId,
        String marketCode,
        String lineOfBusiness,
        String correlationId,
        Map<String, Object> data
) {
    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(claimId, "claimId");
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
