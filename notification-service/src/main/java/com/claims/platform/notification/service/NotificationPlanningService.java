package com.claims.platform.notification.service;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.notification.entity.Notification;

import java.util.List;

/**
 * Turns one claims event or SLA alert into PENDING notifications (one per recipient x channel), rendered in the
 * recipient's language. Nothing is sent here: the dispatcher does that, so a slow provider never holds up the
 * Kafka consumer. Must run inside the consumer's transaction (with the processed_event mark).
 */
public interface NotificationPlanningService {
    /** @return the notifications planned (empty when nobody needs telling) */
    List<Notification> planNotifications(EventEnvelope event);
}
