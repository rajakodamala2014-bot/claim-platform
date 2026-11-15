package com.claims.platform.notification.application;

import com.claims.platform.notification.application.NotificationPorts.NotificationLog;
import com.claims.platform.notification.domain.Notification;

/** N7: a provider reports DELIVERED or BOUNCED for a message it accepted earlier. Unknown ids are 404. */
public class RecordDeliveryStatus {
    private final NotificationLog log;

    public RecordDeliveryStatus(NotificationLog log) {
        this.log = log;
    }

    public Notification record(String provider, String providerMessageId, Notification.Status status) {
        Notification notification = log.byProviderMessage(provider, providerMessageId)
                .orElseThrow(() -> new NotificationException(NotificationException.Code.NOT_FOUND,
                        "No message " + providerMessageId + " from " + provider));
        Notification updated;
        try {
            updated = notification.deliveryReported(status);
        } catch (IllegalArgumentException ex) {
            throw new NotificationException(NotificationException.Code.VALIDATION_FAILED, ex.getMessage());
        }
        log.update(updated);
        return updated;
    }
}
