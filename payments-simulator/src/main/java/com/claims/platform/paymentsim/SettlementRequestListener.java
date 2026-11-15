package com.claims.platform.paymentsim;

import com.claims.platform.common.messaging.Topics;
import com.claims.platform.common.payments.PaymentResult;
import com.claims.platform.common.payments.SettlementRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Component
class SettlementRequestListener {
    private static final Logger log = LoggerFactory.getLogger(SettlementRequestListener.class);

    private final FakeBank bank;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final SimulatorProperties properties;

    SettlementRequestListener(KafkaTemplate<String, String> kafka, ObjectMapper json, SimulatorProperties properties) {
        this.bank = new FakeBank(Clock.systemUTC());
        this.kafka = kafka;
        this.json = json;
        this.properties = properties;
    }

    @KafkaListener(topics = Topics.SETTLEMENT_REQUESTS, groupId = "payments-simulator")
    void onSettlementRequest(ConsumerRecord<String, String> record) throws Exception {
        SettlementRequest request = json.readValue(record.value(), SettlementRequest.class);
        Thread.sleep(properties.processingDelay().toMillis());
        PaymentResult result = bank.process(request);
        kafka.send(Topics.PAYMENT_RESULTS, request.claimId().toString(), json.writeValueAsString(result))
                .get(10, TimeUnit.SECONDS);
        log.info("Settlement {} instruction {} -> {}", request.settlementId(), request.instructionId(), result.status());
    }
}
