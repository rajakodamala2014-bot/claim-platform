package com.claims.platform.testsupport;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

/**
 * Kafka for integration tests, one broker per JVM.
 * <ul>
 *     <li>Docker available (CI) -> Testcontainers {@code apache/kafka}.</li>
 *     <li>No Docker -> spring-kafka-test's in-JVM KRaft broker, so Kafka tests still run locally.</li>
 * </ul>
 */
public final class TestKafka {
    private static String bootstrapServers;

    private TestKafka() {
    }

    public static synchronized String bootstrapServers() {
        if (bootstrapServers == null) {
            boolean forceContainers = "testcontainers".equals(System.getProperty("test.kafka"));
            if (forceContainers || docker()) {
                KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1");
                kafka.start();
                bootstrapServers = kafka.getBootstrapServers();
            } else {
                EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(1, 3);
                broker.afterPropertiesSet();
                Runtime.getRuntime().addShutdownHook(new Thread(broker::destroy));
                bootstrapServers = broker.getBrokersAsString();
            }
        }
        return bootstrapServers;
    }

    /**
     * Points Spring Kafka at the shared broker, creates the given topics (idempotent) and gives this test context
     * its own consumer group: Spring caches contexts, and listeners of several live contexts sharing one group
     * would split the partitions (a test's message could be consumed by another test's context).
     */
    public static void register(DynamicPropertyRegistry registry, String... topics) {
        String servers = bootstrapServers();
        createTopics(topics);
        String group = "it-" + UUID.randomUUID();
        registry.add("spring.kafka.bootstrap-servers", () -> servers);
        registry.add("spring.kafka.consumer.group-id", () -> group);
    }

    public static void createTopics(String... topics) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers()))) {
            for (String topic : topics) {
                try {
                    admin.createTopics(List.of(new NewTopic(topic, 3, (short) 1))).all().get();
                } catch (ExecutionException ex) {
                    if (!(ex.getCause() instanceof TopicExistsException)) {
                        throw new IllegalStateException("Could not create topic " + topic, ex);
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    /** A consumer reading {@code topic} from the beginning, in its own consumer group. */
    public static KafkaConsumer<String, String> consumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    /** Polls until {@code count} records matching {@code filter} arrived (or the timeout passes). */
    public static List<ConsumerRecord<String, String>> poll(KafkaConsumer<String, String> consumer,
                                                            Predicate<ConsumerRecord<String, String>> filter,
                                                            int count, Duration timeout) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (matching.size() < count && System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            records.forEach(r -> {
                if (filter.test(r)) {
                    matching.add(r);
                }
            });
        }
        return matching;
    }

    private static boolean docker() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
