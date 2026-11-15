package com.claims.platform.notification.service;

import com.claims.platform.notification.entity.Notification;

/** Delivery receipts from providers. */
public interface DeliveryStatusService {
    /** Records DELIVERED or BOUNCED for a message the provider accepted earlier; unknown ids are not found. */
    Notification recordDeliveryStatus(String provider, String providerMessageId, Notification.Status status);
}
