package com.claims.platform.insights.infrastructure;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.messaging.Topics;
import com.claims.platform.insights.application.CheckSlaAlerts;
import com.claims.platform.insights.application.CheckSlaAlerts.AlertLog;
import com.claims.platform.insights.application.CheckSlaAlerts.AlertPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.claims.platform.insights.infrastructure.JdbcClaimSummaryStore.ts;

/** Kafka publisher + DB log for SLA alerts, and the 5-minute schedule (one instance via advisory lock). */
public final class SlaAlertAdapters {
    private SlaAlertAdapters() {
    }

    @Component
    public static class KafkaAlertPublisher implements AlertPublisher {
        private final KafkaTemplate<String, String> kafka;
        private final ObjectMapper json;

        public KafkaAlertPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
            this.kafka = kafka;
            this.json = json;
        }

        @Override
        public void publish(EventEnvelope alert) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(Topics.INSIGHTS_ALERTS, alert.claimId().toString(),
                        json.writeValueAsString(alert));
                record.headers().add(new RecordHeader("eventType", alert.type().getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("messageId", alert.eventId().toString().getBytes(StandardCharsets.UTF_8)));
                kafka.send(record).get(10, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException("Could not publish SLA alert for claim " + alert.claimId(), ex);
            }
        }
    }

    @Component
    public static class JdbcAlertLog implements AlertLog {
        private final JdbcClient jdbc;

        public JdbcAlertLog(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public void recordSent(UUID claimId, String kind, Instant slaDueAt) {
            jdbc.sql("INSERT INTO insights.sla_alert (claim_id, kind, sla_due_at) VALUES (:id, :kind, :due) ON CONFLICT DO NOTHING")
                    .param("id", claimId).param("kind", kind).param("due", ts(slaDueAt)).update();
        }
    }

    @Component
    @ConditionalOnProperty(name = "insights.sla.alerts-enabled", havingValue = "true", matchIfMissing = true)
    public static class SlaAlertScheduler {
        private static final long LOCK = 0x51A_A1E7L;
        private final CheckSlaAlerts checkSlaAlerts;
        private final JdbcClient jdbc;
        private final TransactionTemplate tx;

        public SlaAlertScheduler(CheckSlaAlerts checkSlaAlerts, JdbcClient jdbc, TransactionTemplate tx) {
            this.checkSlaAlerts = checkSlaAlerts;
            this.jdbc = jdbc;
            this.tx = tx;
        }

        @Scheduled(fixedDelayString = "${insights.sla.alert-check-interval:5m}", initialDelayString = "${insights.sla.alert-check-interval:5m}")
        public void run() {
            tx.executeWithoutResult(status -> {
                if (Boolean.TRUE.equals(jdbc.sql("SELECT pg_try_advisory_xact_lock(:k)").param("k", LOCK).query(Boolean.class).single())) {
                    checkSlaAlerts.runOnce();
                }
            });
        }
    }
}
