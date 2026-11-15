package com.claims.platform.insights.infrastructure;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.messaging.Topics;
import com.claims.platform.insights.application.ProjectClaimEvent;
import com.claims.platform.messaging.MalformedMessageException;
import com.claims.platform.messaging.ProcessedEvents;
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
public class ClaimEventsListener {
    static final String CONSUMER = "insights-service.claim-events";
    private static final Logger log = LoggerFactory.getLogger(ClaimEventsListener.class);

    private final ProjectClaimEvent projector;
    private final ProcessedEvents processedEvents;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public ClaimEventsListener(ProjectClaimEvent projector, ProcessedEvents processedEvents, TransactionTemplate tx,
                               ObjectMapper json) {
        this.projector = projector;
        this.processedEvents = processedEvents;
        this.tx = tx;
        this.json = json;
    }

    @KafkaListener(topics = Topics.CLAIM_EVENTS) // group: spring.kafka.consumer.group-id (insights-service)
    public void onClaimEvent(ConsumerRecord<String, String> record) {
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
            ProjectClaimEvent.Outcome outcome = projector.apply(event);
            if (outcome == ProjectClaimEvent.Outcome.IGNORED_UNKNOWN_CLAIM) {
                log.warn("{} for unknown claim {} ignored", event.type(), event.claimId());
            }
        });
    }
}
