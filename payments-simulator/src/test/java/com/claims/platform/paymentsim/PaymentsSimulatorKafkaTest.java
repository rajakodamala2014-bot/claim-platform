package com.claims.platform.paymentsim;

import com.claims.platform.common.messaging.Topics;
import com.claims.platform.common.payments.PaymentResult;
import com.claims.platform.common.payments.SettlementRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against an in-JVM Kafka broker (no Docker needed). */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "payments.simulator.processing-delay=0s"
})
@EmbeddedKafka(partitions = 1, topics = {Topics.SETTLEMENT_REQUESTS, Topics.PAYMENT_RESULTS})
class PaymentsSimulatorKafkaTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    ObjectMapper json;

    @Test
    void answersSettlementRequestOnPaymentResultsKeyedByClaimId() throws Exception {
        UUID claimId = UUID.randomUUID();
        SettlementRequest request = new SettlementRequest(UUID.randomUUID(), UUID.randomUUID(), claimId,
                "SG-MTR-2026-000123", "Priya Nair", "DBSSSGSG", "123456789", 420_000, "SGD", Instant.now());

        try (Consumer<String, String> results = resultsConsumer()) {
            kafka.send(Topics.SETTLEMENT_REQUESTS, claimId.toString(), json.writeValueAsString(request)).get();

            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(results, Topics.PAYMENT_RESULTS, Duration.ofSeconds(20));

            assertThat(record.key()).isEqualTo(claimId.toString());
            PaymentResult result = json.readValue(record.value(), PaymentResult.class);
            assertThat(result.status()).isEqualTo(PaymentResult.Status.PAID);
            assertThat(result.instructionId()).isEqualTo(request.instructionId());
            assertThat(result.settlementId()).isEqualTo(request.settlementId());
        }
    }

    private Consumer<String, String> resultsConsumer() {
        var props = KafkaTestUtils.consumerProps("results-test", "false", broker);
        props.put("auto.offset.reset", "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, Topics.PAYMENT_RESULTS);
        return consumer;
    }
}
