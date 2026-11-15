package com.claims.platform.claims.core.infrastructure;

import com.claims.platform.claims.platform.infrastructure.Jdbc;
import com.claims.platform.common.web.Headers;
import com.claims.platform.web.correlation.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Appends a message to platform.outbox in the current transaction. The relay (Phase 5) publishes it to Kafka.
 * The correlation id of the current request travels as a header and inside the envelope.
 */
@Component
public class OutboxWriter {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public OutboxWriter(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void append(UUID messageId, String topic, String key, String eventType, Object payload) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("eventType", eventType);
        CorrelationIdFilter.current().ifPresent(id -> headers.put(Headers.CORRELATION_ID, id));
        jdbc.sql("""
                        INSERT INTO platform.outbox (id, topic, message_key, event_type, payload, headers)
                        VALUES (:id, :topic, :key, :type, :payload::jsonb, :headers::jsonb)
                        """)
                .param("id", messageId)
                .param("topic", topic)
                .param("key", key)
                .param("type", eventType)
                .param("payload", Jdbc.json(json, payload))
                .param("headers", Jdbc.json(json, headers))
                .update();
    }
}
