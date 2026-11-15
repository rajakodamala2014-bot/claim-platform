package com.claims.platform.claims.core.entity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Something that happened to a claim. Persisted as {@code claim_event} (audit trail; CLAIMANT-visible rows
 * form the customer timeline) and, in the same transaction, as an outbox row for {@code claims.claim-events}.
 * {@code type} values are {@link com.claims.platform.common.event.EventTypes}.
 */
public record ClaimEvent(
        UUID eventId,
        String type,
        Visibility visibility,
        String actorId,
        Role actorRole,
        Instant occurredAt,
        Map<String, Object> data
) {
    public enum Visibility {
        /** Shown on the customer's timeline (and staff audit). */
        CLAIMANT,
        /** Staff audit trail only. */
        INTERNAL
    }

    public ClaimEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(occurredAt, "occurredAt");
        // keeps insertion order for readable payloads; nulls are dropped rather than serialised
        Map<String, Object> copy = new LinkedHashMap<>();
        if (data != null) {
            data.forEach((key, value) -> {
                if (value != null) {
                    copy.put(key, value);
                }
            });
        }
        data = java.util.Collections.unmodifiableMap(copy);
    }
}
