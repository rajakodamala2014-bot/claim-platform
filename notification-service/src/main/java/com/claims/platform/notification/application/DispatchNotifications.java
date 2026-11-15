package com.claims.platform.notification.application;

import com.claims.platform.notification.application.NotificationPorts.DeliveryProvider;
import com.claims.platform.notification.application.NotificationPorts.NotificationLog;
import com.claims.platform.notification.application.NotificationPorts.OutboundMessage;
import com.claims.platform.notification.domain.Channel;
import com.claims.platform.notification.domain.Notification;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Sends PENDING notifications through the provider for their channel. A provider error is retried with
 * backoff up to {@code maxAttempts} (3), then the notification is logged FAILED. In-app notifications are
 * "sent" by being placed in the inbox. Runs inside the caller's transaction (rows are locked while sending).
 */
public class DispatchNotifications {
    public static final String INBOX = "inbox";
    private static final int BATCH = 100;

    private final NotificationLog log;
    private final Map<Channel, DeliveryProvider> providers = new EnumMap<>(Channel.class);
    private final int maxAttempts;
    private final Clock clock;

    public DispatchNotifications(NotificationLog log, List<DeliveryProvider> providers, int maxAttempts, Clock clock) {
        this.log = log;
        providers.forEach(p -> this.providers.put(p.channel(), p));
        this.maxAttempts = maxAttempts;
        this.clock = clock;
    }

    public record Result(int sent, int retrying, int failed) {
    }

    public Result runOnce() {
        Instant now = clock.instant();
        int sent = 0, retrying = 0, failed = 0;
        for (Notification n : log.due(now, BATCH)) {
            Notification updated;
            if (n.channel() == Channel.IN_APP) {
                updated = n.sent(INBOX, null, now);
            } else {
                DeliveryProvider provider = providers.get(n.channel());
                try {
                    if (provider == null) {
                        throw new IllegalStateException("No provider for " + n.channel());
                    }
                    String messageId = provider.send(new OutboundMessage(n.id(), n.channel(), n.address(), n.subject(), n.body()));
                    updated = n.sent(provider.name(), messageId, now);
                } catch (RuntimeException ex) {
                    updated = n.attemptFailed(ex.getMessage(), maxAttempts, now);
                }
            }
            log.update(updated);
            switch (updated.status()) {
                case SENT -> sent++;
                case FAILED -> failed++;
                default -> retrying++;
            }
        }
        return new Result(sent, retrying, failed);
    }
}
