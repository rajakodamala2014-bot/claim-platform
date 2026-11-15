package com.claims.platform.notification.service.impl;

import com.claims.platform.notification.entity.Notification;
import com.claims.platform.notification.exception.NotificationException;
import com.claims.platform.notification.repository.NotificationRepository;
import com.claims.platform.notification.service.DeliveryStatusService;
import org.springframework.stereotype.Service;

/** N7: a provider reports DELIVERED or BOUNCED for a message it accepted earlier. Unknown ids are 404. */
@Service
public class DeliveryStatusServiceImpl implements DeliveryStatusService {
    private final NotificationRepository notifications;

    public DeliveryStatusServiceImpl(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    public Notification recordDeliveryStatus(String provider, String providerMessageId, Notification.Status status) {
        Notification notification = notifications.findByProviderMessageId(provider, providerMessageId)
                .orElseThrow(() -> new NotificationException(NotificationException.Code.NOT_FOUND,
                        "No message " + providerMessageId + " from " + provider));
        Notification updated;
        try {
            updated = notification.deliveryReported(status);
        } catch (IllegalArgumentException ex) {
            throw new NotificationException(NotificationException.Code.VALIDATION_FAILED, ex.getMessage());
        }
        notifications.update(updated);
        return updated;
    }
}
