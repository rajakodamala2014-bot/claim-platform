package com.claims.platform.notification.application;

import com.claims.platform.notification.application.NotificationPorts.ClaimContacts;
import com.claims.platform.notification.application.NotificationPorts.NotificationLog;
import com.claims.platform.notification.application.NotificationPorts.Preferences;
import com.claims.platform.notification.application.NotificationPorts.Template;
import com.claims.platform.notification.application.NotificationPorts.Templates;
import com.claims.platform.notification.domain.Notification;
import com.claims.platform.notification.domain.Preference;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * N1-N6: inbox, read receipts, preferences, staff delivery view and template admin. Each method owns its access
 * rule (own inbox only, market scope for staff, managers for templates).
 */
public class NotificationQueries {
    private final NotificationLog log;
    private final Preferences preferences;
    private final ClaimContacts contacts;
    private final Templates templates;
    private final Clock clock;

    public NotificationQueries(NotificationLog log, Preferences preferences, ClaimContacts contacts, Templates templates,
                               Clock clock) {
        this.log = log;
        this.preferences = preferences;
        this.contacts = contacts;
        this.templates = templates;
        this.clock = clock;
    }

    /** N1: my in-app messages, newest first. */
    public List<Notification> inbox(String userId, boolean unreadOnly) {
        return log.inbox(userId, unreadOnly, 50);
    }

    /** N2: only my own messages can be marked read (others look like they don't exist). */
    public void markRead(String userId, UUID notificationId) {
        if (!log.markRead(notificationId, userId, clock.instant())) {
            throw new NotificationException(NotificationException.Code.NOT_FOUND, "Notification " + notificationId + " not found");
        }
    }

    /** N3. */
    public Preference preferences(String userId) {
        return preferences.findPreference(userId).orElse(Preference.defaults(userId));
    }

    /** N4. */
    public Preference updatePreferences(Preference preference) {
        preferences.save(preference);
        return preference;
    }

    /** N5: what was sent about a claim (all recipients/channels) and whether it was delivered. Market-scoped. */
    public List<Notification> forClaim(UUID claimId, Set<String> staffMarkets) {
        var contact = contacts.find(claimId)
                .orElseThrow(() -> new NotificationException(NotificationException.Code.NOT_FOUND, "Claim " + claimId + " not found"));
        if (!staffMarkets.contains(contact.market())) {
            throw new NotificationException(NotificationException.Code.FORBIDDEN_MARKET,
                    "You are not assigned to market " + contact.market());
        }
        return log.forClaim(claimId);
    }

    /** N6. */
    public List<Template> templates() {
        return templates.all();
    }

    /** N6: change the wording; placeholders are kept as typed. */
    public Template updateTemplate(String id, String subject, String body) {
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            throw new NotificationException(NotificationException.Code.VALIDATION_FAILED, "subject and body are required");
        }
        templates.byId(id).orElseThrow(() -> new NotificationException(NotificationException.Code.NOT_FOUND, "Template " + id + " not found"));
        templates.update(id, subject, body);
        return templates.byId(id).orElseThrow();
    }
}
