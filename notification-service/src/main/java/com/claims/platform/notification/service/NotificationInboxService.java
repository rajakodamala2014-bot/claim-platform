package com.claims.platform.notification.service;

import com.claims.platform.notification.entity.Notification;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Reading the notification log: a customer's own inbox, and what was sent about a claim. */
public interface NotificationInboxService {
    /** The user's in-app messages, newest first. */
    List<Notification> listInboxNotifications(String userId, boolean unreadOnly);

    /** Marks one of the user's own messages as read; someone else's message is reported as not found. */
    void markNotificationRead(String userId, UUID notificationId);

    /** Everything sent about a claim, for staff assigned to the claim's market. */
    List<Notification> listNotificationsOfClaim(UUID claimId, Set<String> staffMarkets);
}
