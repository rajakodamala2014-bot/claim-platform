package com.claims.platform.notification.mapper;

import com.claims.platform.notification.dto.request.UpdatePreferenceRequest;
import com.claims.platform.notification.dto.response.ChannelSettingResponse;
import com.claims.platform.notification.dto.response.InboxNotificationResponse;
import com.claims.platform.notification.dto.response.NotificationDeliveryResponse;
import com.claims.platform.notification.dto.response.NotificationPreferenceResponse;
import com.claims.platform.notification.dto.response.TemplateResponse;
import com.claims.platform.notification.entity.Notification;
import com.claims.platform.notification.entity.Preference;
import com.claims.platform.notification.entity.Template;

/** Maps between the notification entities and the API's request / response DTOs. */
public final class NotificationMapper {
    private NotificationMapper() {
    }

    public static InboxNotificationResponse toInboxNotificationResponse(Notification n) {
        return new InboxNotificationResponse(n.id(), n.claimId(), n.claimNumber(), n.eventType(), n.subject(), n.body(),
                n.createdAt(), n.readAt() != null);
    }

    /** Delivery log entry for staff: the recipient's address is masked. */
    public static NotificationDeliveryResponse toDeliveryResponse(Notification n) {
        return new NotificationDeliveryResponse(n.id(), n.recipientId(), n.audience().name(), n.channel().name(),
                maskAddress(n.address()), n.language(), n.eventType(), n.subject(), n.status().name(), n.attempts(),
                n.lastError(), n.sentAt(), n.readAt());
    }

    public static NotificationPreferenceResponse toPreferenceResponse(Preference p) {
        return new NotificationPreferenceResponse(p.language(), new ChannelSettingResponse(p.emailEnabled(), p.email()),
                new ChannelSettingResponse(p.smsEnabled(), p.phone()), new ChannelSettingResponse(p.pushEnabled(), null));
    }

    /** @throws IllegalArgumentException when the request breaks a preference rule (e.g. unsupported language) */
    public static Preference toPreference(String userId, UpdatePreferenceRequest request) {
        return new Preference(userId, request.language(), request.email().address(), request.email().enabled(),
                request.sms().address(), request.sms().enabled(), request.push().enabled());
    }

    public static TemplateResponse toTemplateResponse(Template t) {
        return new TemplateResponse(t.id(), t.eventType(), t.audience().name(),
                t.channel() == null ? null : t.channel().name(), t.language(), t.subject(), t.body());
    }

    static String maskAddress(String address) {
        if (address == null || address.length() <= 4) {
            return address;
        }
        int at = address.indexOf('@');
        if (at > 0) {
            return address.charAt(0) + "***" + address.substring(at);
        }
        return "****" + address.substring(address.length() - 4);
    }
}
