package com.claims.platform.notification.service.impl;

import com.claims.platform.notification.entity.Channel;
import com.claims.platform.notification.entity.Notification;
import com.claims.platform.notification.entity.OutboundMessage;
import com.claims.platform.notification.producer.NotificationSender;
import com.claims.platform.notification.repository.NotificationRepository;
import com.claims.platform.notification.service.NotificationDispatchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationDispatchServiceImpl implements NotificationDispatchService {
    private static final int BATCH = 100;

    private final NotificationRepository notifications;
    private final Map<Channel, NotificationSender> senders = new EnumMap<>(Channel.class);
    private final int maxAttempts;
    private final Clock clock;

    public NotificationDispatchServiceImpl(NotificationRepository notifications, List<NotificationSender> senders,
                                           @Value("${notifications.dispatch.max-attempts:3}") int maxAttempts, Clock clock) {
        this.notifications = notifications;
        senders.forEach(sender -> this.senders.put(sender.channel(), sender));
        this.maxAttempts = maxAttempts;
        this.clock = clock;
    }

    @Override
    public DispatchResult dispatchPendingNotifications() {
        Instant now = clock.instant();
        int sent = 0, retrying = 0, failed = 0;
        for (Notification notification : notifications.findDueForDispatch(now, BATCH)) {
            Notification updated = send(notification, now);
            notifications.update(updated);
            switch (updated.status()) {
                case SENT -> sent++;
                case FAILED -> failed++;
                default -> retrying++;
            }
        }
        return new DispatchResult(sent, retrying, failed);
    }

    private Notification send(Notification n, Instant now) {
        if (n.channel() == Channel.IN_APP) {
            return n.sent(INBOX, null, now);
        }
        NotificationSender sender = senders.get(n.channel());
        try {
            if (sender == null) {
                throw new IllegalStateException("No provider for " + n.channel());
            }
            String messageId = sender.send(new OutboundMessage(n.id(), n.channel(), n.address(), n.subject(), n.body()));
            return n.sent(sender.providerName(), messageId, now);
        } catch (RuntimeException ex) {
            return n.attemptFailed(ex.getMessage(), maxAttempts, now);
        }
    }
}
