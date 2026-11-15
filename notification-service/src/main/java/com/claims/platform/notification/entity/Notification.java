package com.claims.platform.notification.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One message to one recipient on one channel. Also the delivery log: PENDING -> SENT (-> DELIVERED/BOUNCED via
 * provider callback) or FAILED after the last attempt. In-app notifications are the customer's inbox.
 */
public record Notification(
        UUID id,
        String recipientId,
        Audience audience,
        UUID claimId,
        String claimNumber,
        UUID eventId,
        String eventType,
        Channel channel,
        String address,
        String language,
        String subject,
        String body,
        Status status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        String provider,
        String providerMessageId,
        Instant sentAt,
        Instant readAt,
        Instant createdAt
) {
    public enum Status { PENDING, SENT, DELIVERED, BOUNCED, FAILED }

    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(status, "status");
    }

    public static Notification pending(String recipientId, Audience audience, UUID claimId, String claimNumber, UUID eventId,
                                       String eventType, Channel channel, String address, String language, String subject,
                                       String body, Instant now) {
        return new Notification(UUID.randomUUID(), recipientId, audience, claimId, claimNumber, eventId, eventType, channel,
                address, language, subject, body, Status.PENDING, 0, now, null, null, null, null, null, now);
    }

    public Notification sent(String providerName, String messageId, Instant at) {
        return new Notification(id, recipientId, audience, claimId, claimNumber, eventId, eventType, channel, address, language,
                subject, body, Status.SENT, attempts + 1, nextAttemptAt, null, providerName, messageId, at, readAt, createdAt);
    }

    /**
     * A failed attempt: retried with exponential backoff (2 s, 4 s, ...) until {@code maxAttempts}, then FAILED.
     */
    public Notification attemptFailed(String error, int maxAttempts, Instant now) {
        int made = attempts + 1;
        boolean giveUp = made >= maxAttempts;
        Instant next = giveUp ? nextAttemptAt : now.plus(Duration.ofSeconds(1L << made));
        return new Notification(id, recipientId, audience, claimId, claimNumber, eventId, eventType, channel, address, language,
                subject, body, giveUp ? Status.FAILED : Status.PENDING, made, next, error, provider, providerMessageId, sentAt,
                readAt, createdAt);
    }

    /** Provider callback (N7). Only a sent message can be delivered or bounce. */
    public Notification deliveryReported(Status reported) {
        if (reported != Status.DELIVERED && reported != Status.BOUNCED) {
            throw new IllegalArgumentException("Delivery status must be DELIVERED or BOUNCED");
        }
        if (status != Status.SENT && status != reported) {
            return this;
        }
        return new Notification(id, recipientId, audience, claimId, claimNumber, eventId, eventType, channel, address, language,
                subject, body, reported, attempts, nextAttemptAt, lastError, provider, providerMessageId, sentAt, readAt, createdAt);
    }
}
