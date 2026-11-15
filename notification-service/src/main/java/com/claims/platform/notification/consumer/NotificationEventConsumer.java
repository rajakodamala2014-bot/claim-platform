package com.claims.platform.notification.consumer;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.messaging.Topics;
import com.claims.platform.messaging.exception.MalformedMessageException;
import com.claims.platform.messaging.consumer.ProcessedEvents;
import com.claims.platform.notification.service.NotificationPlanningService;
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
public class NotificationEventConsumer {
    private final NotificationPlanningService planningService;
    private final ProcessedEvents processedEvents;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public NotificationEventConsumer(NotificationPlanningService planningService, ProcessedEvents processedEvents,
                                     TransactionTemplate tx, ObjectMapper json) {
        this.planningService = planningService;
        this.processedEvents = processedEvents;
        this.tx = tx;
        this.json = json;
    }

    @KafkaListener(topics = Topics.CLAIM_EVENTS) // group: spring.kafka.consumer.group-id (notification-service)
    public void consumeClaimEvent(ConsumerRecord<String, String> record) {
        planNotificationsOnce("notification-service.claim-events", record);
    }

    @KafkaListener(topics = Topics.INSIGHTS_ALERTS)
    public void consumeSlaAlert(ConsumerRecord<String, String> record) {
        planNotificationsOnce("notification-service.alerts", record);
    }

    private void planNotificationsOnce(String consumer, ConsumerRecord<String, String> record) {
        EventEnvelope event;
        try {
            event = json.readValue(record.value(), EventEnvelope.class);
        } catch (Exception ex) {
            throw new MalformedMessageException("Unreadable event on " + record.topic() + " at offset " + record.offset(), ex);
        }
        tx.executeWithoutResult(status -> {
            if (processedEvents.markProcessed(consumer, event.eventId().toString())) {
                planningService.planNotifications(event);
            }
        });
    }
}
