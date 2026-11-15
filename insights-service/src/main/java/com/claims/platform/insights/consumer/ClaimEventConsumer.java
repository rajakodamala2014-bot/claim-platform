package com.claims.platform.insights.consumer;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.messaging.Topics;
import com.claims.platform.insights.service.ClaimProjectionService;
import com.claims.platform.insights.service.ClaimProjectionService.ProjectionOutcome;
import com.claims.platform.messaging.exception.MalformedMessageException;
import com.claims.platform.messaging.consumer.ProcessedEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Builds the read models from claims.claim-events. Idempotent (processed_event in the same transaction as the
 * projection); unparseable events go to the DLQ, failing ones after 5 attempts.
 */
@Component
public class ClaimEventConsumer {
    static final String CONSUMER = "insights-service.claim-events";
    private static final Logger log = LoggerFactory.getLogger(ClaimEventConsumer.class);

    private final ClaimProjectionService projectionService;
    private final ProcessedEvents processedEvents;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public ClaimEventConsumer(ClaimProjectionService projectionService, ProcessedEvents processedEvents,
                              TransactionTemplate tx, ObjectMapper json) {
        this.projectionService = projectionService;
        this.processedEvents = processedEvents;
        this.tx = tx;
        this.json = json;
    }

    @KafkaListener(topics = Topics.CLAIM_EVENTS) // group: spring.kafka.consumer.group-id (insights-service)
    public void consumeClaimEvent(ConsumerRecord<String, String> record) {
        EventEnvelope event;
        try {
            event = json.readValue(record.value(), EventEnvelope.class);
        } catch (Exception ex) {
            throw new MalformedMessageException("Unreadable claim event at offset " + record.offset(), ex);
        }
        tx.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(CONSUMER, event.eventId().toString())) {
                return;
            }
            ProjectionOutcome outcome = projectionService.projectClaimEvent(event);
            if (outcome == ProjectionOutcome.IGNORED_UNKNOWN_CLAIM) {
                log.warn("{} for unknown claim {} ignored", event.type(), event.claimId());
            }
        });
    }
}
