package com.claims.platform.common.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class EventEnvelopeTest {

    @Test
    void dataIsDefensivelyCopiedAndImmutable() {
        Map<String, Object> data = new HashMap<>(Map.of("status", "SUBMITTED"));
        EventEnvelope envelope = envelope(data);

        data.put("status", "CLOSED");

        assertThat(envelope.data()).containsEntry("status", "SUBMITTED");
        assertThat(envelope.data()).isUnmodifiable();
    }

    @Test
    void nullDataBecomesEmptyMap() {
        assertThat(envelope(null).data()).isEmpty();
    }

    @Test
    void identityFieldsAreMandatory() {
        assertThatNullPointerException()
                .isThrownBy(() -> new EventEnvelope(null, "ClaimSubmitted", 1, Instant.now(), UUID.randomUUID(),
                        "SG", "MOTOR", "corr", Map.of()))
                .withMessage("eventId");
    }

    private static EventEnvelope envelope(Map<String, Object> data) {
        return new EventEnvelope(UUID.randomUUID(), "ClaimSubmitted", 1, Instant.now(), UUID.randomUUID(),
                "SG", "MOTOR", "corr-1", data);
    }
}
