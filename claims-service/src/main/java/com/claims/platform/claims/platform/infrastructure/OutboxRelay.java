package com.claims.platform.claims.platform.infrastructure;

import com.claims.platform.common.messaging.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publishes platform.outbox rows to Kafka (~every 500 ms).
 * <ul>
 *     <li>Single publisher: a transaction-scoped Postgres advisory lock means only one instance relays at a
 *     time, so rows go out strictly in {@code seq} order (per-claim ordering on Kafka depends on it).</li>
 *     <li>At-least-once: a row is marked published only after the broker acknowledged it; a crash in between
 *     re-sends it, which consumers absorb via processed_event / eventId.</li>
 *     <li>Stops at the first failure so nothing overtakes it; the failure is recorded on the row.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "claims.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    /** Arbitrary constant identifying the relay's advisory lock. */
    private static final long RELAY_LOCK = 0x0B0C_5E1AL;

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final int batchSize;

    public OutboxRelay(JdbcClient jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka, ObjectMapper json,
                       @Value("${claims.outbox.relay.batch-size:200}") int batchSize) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.json = json;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${claims.outbox.relay.interval:500ms}")
    public void tick() {
        try {
            publishBatch();
        } catch (RuntimeException ex) {
            log.warn("Outbox relay run failed: {}", ex.getMessage());
        }
    }

    /** @return number of rows published in this run */
    public int publishBatch() {
        Integer published = tx.execute(status -> {
            boolean leader = jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)").param("key", RELAY_LOCK)
                    .query(Boolean.class).single();
            if (!leader) {
                return 0;
            }
            List<Row> rows = jdbc.sql("""
                            SELECT id, topic, message_key, payload::text AS payload, headers::text AS headers
                            FROM platform.outbox WHERE published_at IS NULL ORDER BY seq LIMIT :limit
                            """)
                    .param("limit", batchSize)
                    .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("topic"),
                            rs.getString("message_key"), rs.getString("payload"), rs.getString("headers")))
                    .list();
            int sent = 0;
            for (Row row : rows) {
                try {
                    kafka.send(toRecord(row)).get(10, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    String error = ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                            ? ex.getCause().toString() : ex.toString();
                    jdbc.sql("UPDATE platform.outbox SET attempts = attempts + 1, last_error = :error, updated_at = now() "
                                    + "WHERE id = :id")
                            .param("error", error).param("id", row.id()).update();
                    log.warn("Outbox row {} to {} not published (will retry): {}", row.id(), row.topic(), error);
                    break;
                }
                // Payments has the bank account now: keep only its last four digits in our copy
                jdbc.sql("""
                                UPDATE platform.outbox SET published_at = now(), attempts = attempts + 1, last_error = NULL,
                                    updated_at = now(),
                                    payload = CASE WHEN topic = :settlementTopic AND payload->>'accountNumber' IS NOT NULL
                                        THEN jsonb_set(payload, '{accountNumber}',
                                                       to_jsonb('****' || right(payload->>'accountNumber', 4)))
                                        ELSE payload END
                                WHERE id = :id
                                """)
                        .param("id", row.id()).param("settlementTopic", Topics.SETTLEMENT_REQUESTS).update();
                sent++;
            }
            return sent;
        });
        return published == null ? 0 : published;
    }

    private ProducerRecord<String, String> toRecord(Row row) {
        ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.key(), row.payload());
        Map<String, Object> headers = Jdbc.jsonMap(json, row.headers());
        headers.forEach((name, value) -> {
            if (value != null) {
                record.headers().add(new RecordHeader(name, value.toString().getBytes(StandardCharsets.UTF_8)));
            }
        });
        record.headers().add(new RecordHeader("messageId", row.id().toString().getBytes(StandardCharsets.UTF_8)));
        return record;
    }

    private record Row(UUID id, String topic, String key, String payload, String headers) {
    }
}
