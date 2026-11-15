package com.claims.platform.messaging;

import com.claims.platform.messaging.exception.MalformedMessageException;
import com.claims.platform.messaging.config.MessagingAutoConfiguration;
import com.claims.platform.messaging.consumer.ProcessedEvents;
import com.claims.platform.testsupport.TestKafka;
import com.claims.platform.testsupport.TestPostgres;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared consumer policy against a real broker and database: 5 attempts then DLQ; idempotent marks. */
@SpringBootTest(classes = MessagingIT.TestApp.class, properties = {
        "platform.security.dev-secret=test-secret-test-secret-test-secret-123",
        "platform.messaging.processed-event-table=public.processed_event",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"})
class MessagingIT {
    static final String TOPIC = "messaging-it.events";

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestPostgres.registerFreshDatabase(registry);
        TestKafka.register(registry, TOPIC, TOPIC + ".dlq");
    }

    @SpringBootApplication
    @org.springframework.context.annotation.Import(FlakyListener.class)
    static class TestApp {
    }

    @Component
    static class FlakyListener {
        final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

        @KafkaListener(topics = TOPIC, groupId = "messaging-it")
        void on(ConsumerRecord<String, String> record) {
            attempts.computeIfAbsent(record.value(), v -> new AtomicInteger()).incrementAndGet();
            if (record.value().startsWith("poison")) {
                throw new IllegalStateException("boom " + record.value());
            }
            if (record.value().startsWith("garbage")) {
                throw new MalformedMessageException("cannot parse", null);
            }
        }
    }

    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    FlakyListener listener;
    @Autowired
    ProcessedEvents processedEvents;
    @Autowired
    JdbcTemplate sql;

    @Test
    void failingRecordIsTriedFiveTimesThenDeadLetteredWithItsKey() throws Exception {
        try (KafkaConsumer<String, String> dlq = TestKafka.consumer(TOPIC + ".dlq")) {
            kafka.send(TOPIC, "claim-1", "poison-1").get();
            kafka.send(TOPIC, "claim-1", "ok-after-poison").get();

            List<ConsumerRecord<String, String>> dead = TestKafka.poll(dlq, r -> r.value().equals("poison-1"), 1, Duration.ofSeconds(30));

            assertThat(dead).singleElement().satisfies(r -> {
                assertThat(r.key()).isEqualTo("claim-1");
                assertThat(new String(r.headers().lastHeader("kafka_dlt-exception-message").value(), StandardCharsets.UTF_8))
                        .contains("boom poison-1");
            });
            assertThat(listener.attempts.get("poison-1")).hasValue(MessagingAutoConfiguration.MAX_ATTEMPTS);
            awaitAttempts("ok-after-poison", 1);
        }
    }

    @Test
    void malformedRecordSkipsRetries() throws Exception {
        try (KafkaConsumer<String, String> dlq = TestKafka.consumer(TOPIC + ".dlq")) {
            kafka.send(TOPIC, "claim-2", "garbage-1").get();

            assertThat(TestKafka.poll(dlq, r -> r.value().equals("garbage-1"), 1, Duration.ofSeconds(30))).hasSize(1);
            assertThat(listener.attempts.get("garbage-1")).hasValue(1);
        }
    }

    @Test
    void processedEventsAreMarkedOncePerConsumer() {
        sql.execute("CREATE TABLE IF NOT EXISTS public.processed_event (consumer VARCHAR(100), message_id VARCHAR(200), "
                + "processed_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (consumer, message_id))");

        assertThat(processedEvents.markProcessed("insights", "evt-1")).isTrue();
        assertThat(processedEvents.markProcessed("insights", "evt-1")).isFalse();
        assertThat(processedEvents.markProcessed("notifications", "evt-1")).isTrue();
    }

    private void awaitAttempts(String value, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline && (listener.attempts.get(value) == null || listener.attempts.get(value).get() < expected)) {
            Thread.sleep(100);
        }
        assertThat(listener.attempts.get(value)).hasValue(expected);
    }
}
