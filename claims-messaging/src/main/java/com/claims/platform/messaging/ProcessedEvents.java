package com.claims.platform.messaging;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.regex.Pattern;

/**
 * Consumer-side idempotency. Call {@link #markProcessed} in the same transaction as the side effects: a
 * redelivered message finds its row and is skipped; if processing fails, the row rolls back with it.
 * Each service owns a {@code <schema>.processed_event(consumer, message_id, processed_at)} table.
 */
public class ProcessedEvents {
    private static final Pattern TABLE = Pattern.compile("[a-z_][a-z0-9_]*\\.[a-z_][a-z0-9_]*");

    private final JdbcClient jdbc;
    private final String insert;

    public ProcessedEvents(JdbcClient jdbc, String table) {
        if (table == null || !TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("platform.messaging.processed-event-table must be schema.table, was " + table);
        }
        this.jdbc = jdbc;
        this.insert = "INSERT INTO " + table + " (consumer, message_id) VALUES (:consumer, :id) ON CONFLICT DO NOTHING";
    }

    /** @return true the first time a consumer sees this message id; false for a duplicate (skip it) */
    public boolean markProcessed(String consumer, String messageId) {
        return jdbc.sql(insert).param("consumer", consumer).param("id", messageId).update() == 1;
    }
}
