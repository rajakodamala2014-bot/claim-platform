package com.claims.platform.claims.core.producer.impl;

import com.claims.platform.claims.platform.common.JdbcSupport;
import com.claims.platform.common.web.Headers;
import com.claims.platform.web.common.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Appends a message to platform.outbox in the current transaction. The relay ({@code KafkaOutboxPublisher}) publishes it to Kafka.
 * The correlation id of the current request travels as a header and inside the envelope.
 */
@Component
public class OutboxClaimEventProducer {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public OutboxClaimEventProducer(JdbcClient jdbc, ObjectMapper json) {
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
                .param("payload", JdbcSupport.json(json, payload))
                .param("headers", JdbcSupport.json(json, headers))
                .update();
    }
}
