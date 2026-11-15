package com.claims.platform.claims.settlement.consumer;

import com.claims.platform.claims.settlement.service.PaymentResultService;
import com.claims.platform.common.messaging.Topics;
import com.claims.platform.common.payments.PaymentResult;
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
 * Consumes {@code payments.payment-results} (keyed by claimId, so results for one claim arrive in order).
 * Idempotent via processed_event (same transaction as the claim update); unparseable messages go straight to
 * the DLQ, others after 5 failed attempts (shared policy in claims-messaging).
 */
@Component
public class PaymentResultConsumer {
    static final String CONSUMER = "claims-service.payment-results";
    private static final Logger log = LoggerFactory.getLogger(PaymentResultConsumer.class);

    private final PaymentResultService paymentResultService;
    private final ProcessedEvents processedEvents;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public PaymentResultConsumer(PaymentResultService paymentResultService, ProcessedEvents processedEvents,
                                  TransactionTemplate tx, ObjectMapper json) {
        this.paymentResultService = paymentResultService;
        this.processedEvents = processedEvents;
        this.tx = tx;
        this.json = json;
    }

    @KafkaListener(topics = Topics.PAYMENT_RESULTS) // group: spring.kafka.consumer.group-id (claims-service)
    public void onPaymentResult(ConsumerRecord<String, String> record) {
        PaymentResult result = parse(record);
        tx.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(CONSUMER, result.instructionId() + ":" + result.status())) {
                log.info("Duplicate payment result for instruction {} ignored", result.instructionId());
                return;
            }
            PaymentResultService.PaymentResultOutcome outcome = paymentResultService.recordPaymentResult(result);
            if (outcome == PaymentResultService.PaymentResultOutcome.UNKNOWN_CLAIM) {
                log.warn("Payment result for unknown claim {} (settlement {})", result.claimId(), result.settlementId());
            } else {
                log.info("Payment result {} for settlement {}: {}", result.status(), result.settlementId(), outcome);
            }
        });
    }

    private PaymentResult parse(ConsumerRecord<String, String> record) {
        try {
            return json.readValue(record.value(), PaymentResult.class);
        } catch (Exception ex) {
            throw new MalformedMessageException("Unreadable payment result at offset " + record.offset(), ex);
        }
    }
}
