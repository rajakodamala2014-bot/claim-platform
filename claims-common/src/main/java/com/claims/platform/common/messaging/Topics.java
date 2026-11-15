package com.claims.platform.common.messaging;

/**
 * Kafka topics. Every message is keyed by claimId so all events of one claim stay ordered.
 * Partition counts and retention are set where the topics are created (docker-compose kafka-init).
 */
public final class Topics {
    /** Every claim change. Producer: claims-service (outbox). Consumers: insights-service, notification-service. 12 partitions / 30d. */
    public static final String CLAIM_EVENTS = "claims.claim-events";
    /** Payment instructions. Producer: claims-service (outbox). Consumer: Payments system. 6 partitions / 30d. */
    public static final String SETTLEMENT_REQUESTS = "payments.settlement-requests";
    /** Payment outcomes. Producer: Payments system. Consumer: claims-service. 6 partitions / 30d. */
    public static final String PAYMENT_RESULTS = "payments.payment-results";
    /** SLA warnings. Producer: insights-service. Consumer: notification-service. 3 partitions / 7d. */
    public static final String INSIGHTS_ALERTS = "insights.alerts";

    /** Suffix of the dead-letter topic for messages that failed 5 times (14d retention). */
    public static final String DLQ_SUFFIX = ".dlq";

    private Topics() {
    }

    public static String dlq(String topic) {
        return topic + DLQ_SUFFIX;
    }
}
