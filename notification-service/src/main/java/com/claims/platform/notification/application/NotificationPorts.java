package com.claims.platform.notification.application;

import com.claims.platform.notification.domain.Audience;
import com.claims.platform.notification.domain.Channel;
import com.claims.platform.notification.domain.Notification;
import com.claims.platform.notification.domain.Preference;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ports of the notification service (implemented in infrastructure). */
public final class NotificationPorts {
    private NotificationPorts() {
    }

    /** Minimal claim facts, projected from claims events, so Notifications never calls Claims. */
    public record ClaimContact(UUID claimId, String claimNumber, String claimantId, String market, String lineOfBusiness,
                               String handlerId, String teamId) {
        public ClaimContact assignedTo(String handler, String team) {
            return new ClaimContact(claimId, claimNumber, claimantId, market, lineOfBusiness, handler, team);
        }
    }

    public interface ClaimContacts {
        Optional<ClaimContact> find(UUID claimId);

        void save(ClaimContact contact);
    }

    public record StaffMember(String userId, String name, String email, String role, String teamId) {
    }

    public interface StaffDirectory {
        Optional<StaffMember> findStaff(String userId);

        List<StaffMember> teamLeadsOf(String teamId);
    }

    public interface Preferences {
        Optional<Preference> findPreference(String userId);

        void save(Preference preference);
    }

    public record Template(String id, String eventType, Audience audience, Channel channel, String language,
                           String subject, String body) {
    }

    public interface Templates {
        /** Exact match only; fallbacks are decided by the caller. {@code channel} null = the any-channel template. */
        Optional<Template> find(String eventType, Audience audience, Channel channel, String language);

        List<Template> all();

        Optional<Template> byId(String id);

        void update(String id, String subject, String body);
    }

    public interface NotificationLog {
        /** Adds a notification; a duplicate (same event, recipient, channel) is silently ignored. */
        void add(Notification notification);

        void update(Notification notification);

        /** PENDING notifications due for (re)sending, locked for this dispatcher run. */
        List<Notification> due(Instant now, int limit);

        List<Notification> inbox(String recipientId, boolean unreadOnly, int limit);

        boolean markRead(UUID id, String recipientId, Instant at);

        List<Notification> forClaim(UUID claimId);

        Optional<Notification> byProviderMessage(String provider, String providerMessageId);
    }

    /** A message about to leave through a provider. */
    public record OutboundMessage(UUID notificationId, Channel channel, String address, String subject, String body) {
    }

    /** E-mail / SMS / push provider (sync HTTP in real life). Throws on failure; returns the provider's message id. */
    public interface DeliveryProvider {
        String name();

        Channel channel();

        String send(OutboundMessage message);
    }
}
