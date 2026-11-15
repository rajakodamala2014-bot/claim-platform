package com.claims.platform.notification.service.impl;

import com.claims.platform.notification.entity.ClaimContact;
import com.claims.platform.notification.entity.Notification;
import com.claims.platform.notification.exception.NotificationException;
import com.claims.platform.notification.repository.ClaimContactRepository;
import com.claims.platform.notification.repository.NotificationRepository;
import com.claims.platform.notification.service.NotificationInboxService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** N1, N2, N5. Each method owns its access rule (own inbox only, market scope for staff). */
@Service
public class NotificationInboxServiceImpl implements NotificationInboxService {
    private static final int INBOX_LIMIT = 50;

    private final NotificationRepository notifications;
    private final ClaimContactRepository contacts;
    private final Clock clock;

    public NotificationInboxServiceImpl(NotificationRepository notifications, ClaimContactRepository contacts, Clock clock) {
        this.notifications = notifications;
        this.contacts = contacts;
        this.clock = clock;
    }

    @Override
    public List<Notification> listInboxNotifications(String userId, boolean unreadOnly) {
        return notifications.findInbox(userId, unreadOnly, INBOX_LIMIT);
    }

    @Override
    public void markNotificationRead(String userId, UUID notificationId) {
        if (!notifications.markRead(notificationId, userId, clock.instant())) {
            throw new NotificationException(NotificationException.Code.NOT_FOUND, "Notification " + notificationId + " not found");
        }
    }

    @Override
    public List<Notification> listNotificationsOfClaim(UUID claimId, Set<String> staffMarkets) {
        ClaimContact contact = contacts.findByClaimId(claimId)
                .orElseThrow(() -> new NotificationException(NotificationException.Code.NOT_FOUND, "Claim " + claimId + " not found"));
        if (!staffMarkets.contains(contact.market())) {
            throw new NotificationException(NotificationException.Code.FORBIDDEN_MARKET,
                    "You are not assigned to market " + contact.market());
        }
        return notifications.findByClaimId(claimId);
    }
}
