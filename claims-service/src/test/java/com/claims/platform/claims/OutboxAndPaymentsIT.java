package com.claims.platform.claims;

import com.claims.platform.claims.core.repository.ClaimRepository;
import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimFixture;
import com.claims.platform.claims.core.entity.ClaimStatus;
import com.claims.platform.claims.platform.producer.impl.KafkaOutboxPublisher;
import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.common.messaging.Topics;
import com.claims.platform.common.payments.PaymentResult;
import com.claims.platform.common.payments.SettlementRequest;
import com.claims.platform.testsupport.TestKafka;
import com.claims.platform.testsupport.TestPostgres;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.claims.platform.claims.core.entity.ClaimFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox relay -> Kafka (order, keys, headers, at-least-once bookkeeping) and the payment-results consumer
 * (settles the claim, idempotent, DLQ for garbage), against PostgreSQL 16 and a real Kafka broker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"claims.outbox.relay.interval=1h", "claims.jobs.enabled=false"})
@ActiveProfiles("dev")
class OutboxAndPaymentsIT {
    private static final AtomicLong SEQ = new AtomicLong(7000);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestPostgres.registerFreshDatabase(registry);
        TestKafka.register(registry, ClaimsTopics.ALL);
    }

    @Autowired
    ClaimRepository claims;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    KafkaOutboxPublisher relay;
    @Autowired
    JdbcTemplate sql;
    @Autowired
    ObjectMapper json;
    @Autowired
    KafkaTemplate<String, String> kafka;

    @Test
    void relayPublishesEachClaimsEventsInOrderKeyedByClaimWithHeaders() throws Exception {
        long seq = SEQ.incrementAndGet();
        Claim claim = ClaimFixture.submitted(seq);
        claim.confirmCover(Actor.SYSTEM, at(1));
        claim.assign(ARUN.id(), "team-sg-motor", ARUN, at(2));
        claim.startAssessment(ARUN, at(3));
        claim.changeReserve(sgd("5000"), "Initial", ARUN, at(4));
        save(claim);
        String key = claim.id().toString();

        try (KafkaConsumer<String, String> events = TestKafka.consumer(Topics.CLAIM_EVENTS)) {
            assertThat(publishAll()).isGreaterThanOrEqualTo(5);

            List<ConsumerRecord<String, String>> received = TestKafka.poll(events, r -> key.equals(r.key()), 5, Duration.ofSeconds(20));

            assertThat(received).extracting(r -> header(r, "eventType")).containsExactly(EventTypes.CLAIM_SUBMITTED,
                    EventTypes.CLAIM_TRIAGED, EventTypes.CLAIM_ASSIGNED, EventTypes.ASSESSMENT_STARTED, EventTypes.RESERVE_CHANGED);
            assertThat(received).extracting(ConsumerRecord::partition).containsOnly(received.getFirst().partition());
            EventEnvelope first = json.readValue(received.getFirst().value(), EventEnvelope.class);
            assertThat(first.claimId()).isEqualTo(claim.id().value());
            assertThat(first.marketCode()).isEqualTo("SG");
            assertThat(first.data()).containsEntry("claimNumber", claim.number().value()).containsEntry("actorId", "cust-priya");
            assertThat(header(received.getFirst(), "messageId")).isEqualTo(first.eventId().toString());
        }
        assertThat(sql.queryForObject("SELECT count(*) FROM platform.outbox WHERE message_key = ? AND published_at IS NULL",
                Integer.class, key)).isZero();
        assertThat(publishAll()).as("nothing left to publish").isZero();
    }

    @Test
    void settlementInstructionGoesToPaymentsAndThePaymentResultSettlesTheClaim() throws Exception {
        // instruct on an approved claim here, so the instruction is recorded (fixture events are cleared)
        Claim fresh = ClaimFixture.inStatus(ClaimStatus.APPROVED, SEQ.incrementAndGet());
        UUID settlementId = UUID.randomUUID();
        UUID instructionId = UUID.randomUUID();
        fresh.instructSettlement(settlementId, instructionId, PAYEE, sgd("4200"), ARUN, at(50));
        save(fresh);

        try (KafkaConsumer<String, String> requests = TestKafka.consumer(Topics.SETTLEMENT_REQUESTS)) {
            publishAll();
            ConsumerRecord<String, String> request = TestKafka.poll(requests,
                    r -> fresh.id().toString().equals(r.key()), 1, Duration.ofSeconds(20)).getFirst();
            SettlementRequest instruction = json.readValue(request.value(), SettlementRequest.class);
            assertThat(instruction.instructionId()).isEqualTo(instructionId);
            assertThat(instruction.amountMinor()).isEqualTo(420_000);
            assertThat(instruction.accountNumber()).isEqualTo("123456789");
        }
        // once Payments has it, the outbox copy keeps only the last four digits
        assertThat(sql.queryForObject("SELECT payload->>'accountNumber' FROM platform.outbox WHERE id = ?",
                String.class, instructionId)).isEqualTo("****6789");

        PaymentResult paid = new PaymentResult(instructionId, settlementId, fresh.id().value(), PaymentResult.Status.PAID,
                "PAY-XYZ", null, 420_000, "SGD", Instant.now());
        kafka.send(Topics.PAYMENT_RESULTS, fresh.id().toString(), json.writeValueAsString(paid)).get();
        kafka.send(Topics.PAYMENT_RESULTS, fresh.id().toString(), json.writeValueAsString(paid)).get(); // redelivery

        await(() -> claims.findById(fresh.id()).orElseThrow().status() == ClaimStatus.SETTLED);
        Claim settled = claims.findById(fresh.id()).orElseThrow();
        assertThat(settled.outstandingReserve().isZero()).isTrue();
        Thread.sleep(1000); // give the duplicate time to be (not) applied
        assertThat(sql.queryForObject("SELECT count(*) FROM core.claim_event WHERE claim_id = ? AND event_type = 'PaymentConfirmed'",
                Integer.class, fresh.id().value())).isEqualTo(1);
    }

    @Test
    void unreadablePaymentResultGoesToTheDeadLetterTopic() throws Exception {
        String key = "garbage-" + UUID.randomUUID();
        try (KafkaConsumer<String, String> dlq = TestKafka.consumer(Topics.dlq(Topics.PAYMENT_RESULTS))) {
            kafka.send(Topics.PAYMENT_RESULTS, key, "{not json").get();

            // other cached test contexts consume the topic in their own groups and may dead-letter it too
            List<ConsumerRecord<String, String>> dead = TestKafka.poll(dlq, r -> key.equals(r.key()), 1, Duration.ofSeconds(30));
            assertThat(dead).isNotEmpty();
            assertThat(dead.getFirst().value()).isEqualTo("{not json");
            assertThat(header(dead.getFirst(), "kafka_dlt-exception-cause-fqcn"))
                    .isEqualTo("com.claims.platform.messaging.exception.MalformedMessageException");
        }
    }

    private int publishAll() {
        int total = 0;
        int sent;
        while ((sent = relay.publishBatch()) > 0) {
            total += sent;
        }
        return total;
    }

    private void save(Claim claim) {
        tx.executeWithoutResult(s -> claims.save(claim));
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static void await(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline && !condition.get()) {
            Thread.sleep(200);
        }
        assertThat(condition.get()).as("condition met within 30s").isTrue();
    }
}
