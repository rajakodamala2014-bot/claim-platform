package com.claims.platform.notification.repository;

import com.claims.platform.notification.entity.Notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The notification log: everything planned, sent, failed and read. */
public interface NotificationRepository {
    /** Adds a notification; a duplicate (same event, recipient, channel) is silently ignored. */
    void saveIfAbsent(Notification notification);

    void update(Notification notification);

    /** PENDING notifications due for (re)sending, locked for this dispatcher run. */
    List<Notification> findDueForDispatch(Instant now, int limit);

    List<Notification> findInbox(String recipientId, boolean unreadOnly, int limit);

    boolean markRead(UUID id, String recipientId, Instant at);

    List<Notification> findByClaimId(UUID claimId);

    Optional<Notification> findByProviderMessageId(String provider, String providerMessageId);
}
