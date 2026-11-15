package com.claims.platform.notification.infrastructure;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.messaging.Topics;
import com.claims.platform.messaging.MalformedMessageException;
import com.claims.platform.messaging.ProcessedEvents;
import com.claims.platform.notification.application.PlanNotifications;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consumes claims.claim-events and insights.alerts. Planning (who gets what) happens in the same transaction
 * as the processed_event mark; sending happens later in the dispatcher, so providers never slow consumption.
 */
@Component
public class EventListeners {
    private final PlanNotifications plan;
    private final ProcessedEvents processedEvents;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public EventListeners(PlanNotifications plan, ProcessedEvents processedEvents, TransactionTemplate tx, ObjectMapper json) {
        this.plan = plan;
        this.processedEvents = processedEvents;
        this.tx = tx;
        this.json = json;
    }

    @KafkaListener(topics = Topics.CLAIM_EVENTS) // group: spring.kafka.consumer.group-id (notification-service)
    public void onClaimEvent(ConsumerRecord<String, String> record) {
        handle("notification-service.claim-events", record);
    }

    @KafkaListener(topics = Topics.INSIGHTS_ALERTS)
    public void onAlert(ConsumerRecord<String, String> record) {
        handle("notification-service.alerts", record);
    }

    private void handle(String consumer, ConsumerRecord<String, String> record) {
        EventEnvelope event;
        try {
            event = json.readValue(record.value(), EventEnvelope.class);
        } catch (Exception ex) {
            throw new MalformedMessageException("Unreadable event on " + record.topic() + " at offset " + record.offset(), ex);
        }
        tx.executeWithoutResult(status -> {
            if (processedEvents.markProcessed(consumer, event.eventId().toString())) {
                plan.plan(event);
            }
        });
    }
}
