package com.claims.platform.messaging.config;

import com.claims.platform.messaging.exception.MalformedMessageException;
import com.claims.platform.messaging.consumer.ProcessedEvents;
import com.claims.platform.common.messaging.Topics;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Every {@code @KafkaListener} in the platform gets the same failure policy: up to 5 attempts (1 + 4 retries,
 * 500 ms apart), then the record goes to {@code <topic>.dlq} with the original key and exception headers, so
 * one bad message never blocks its partition. Malformed messages skip the retries.
 */
@AutoConfiguration(after = {KafkaAutoConfiguration.class, JdbcClientAutoConfiguration.class})
@ConditionalOnClass({KafkaTemplate.class, JdbcClient.class})
public class MessagingAutoConfiguration {
    public static final int MAX_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(MessagingAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    @ConditionalOnBean(KafkaTemplate.class)
    CommonErrorHandler deadLetterErrorHandler(KafkaTemplate<?, ?> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template, (record, ex) -> {
            log.error("Sending record from {} (key {}) to DLQ after failure: {}", record.topic(), record.key(), ex.getMessage());
            return new TopicPartition(Topics.dlq(record.topic()), -1); // -1: partition chosen by key
        });
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(500L, MAX_ATTEMPTS - 1));
        handler.addNotRetryableExceptions(MalformedMessageException.class);
        return handler;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("platform.messaging.processed-event-table")
    ProcessedEvents processedEvents(JdbcClient jdbc, @Value("${platform.messaging.processed-event-table}") String table) {
        return new ProcessedEvents(jdbc, table);
    }
}
