package com.claims.platform.insights.producer.impl;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.messaging.Topics;
import com.claims.platform.insights.producer.SlaAlertProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Keyed by claim id, waits for the broker's acknowledgement so a failed send fails the alert run. */
@Component
public class KafkaSlaAlertProducer implements SlaAlertProducer {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public KafkaSlaAlertProducer(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @Override
    public void publishAlert(EventEnvelope alert) {
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
