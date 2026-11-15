package com.claims.platform.notification.api;

import com.claims.platform.notification.application.NotificationException;
import com.claims.platform.notification.application.NotificationPorts.Template;
import com.claims.platform.notification.application.NotificationQueries;
import com.claims.platform.notification.application.RecordDeliveryStatus;
import com.claims.platform.notification.domain.Notification;
import com.claims.platform.notification.domain.Preference;
import com.claims.platform.web.security.PlatformUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** N1-N7. */
@RestController
public class NotificationController {
    private final NotificationQueries queries;
    private final RecordDeliveryStatus recordDeliveryStatus;
    private final byte[] callbackSecret;

    public NotificationController(NotificationQueries queries, RecordDeliveryStatus recordDeliveryStatus,
                                  @Value("${notifications.provider-callback-secret}") String callbackSecret) {
        if (callbackSecret == null || callbackSecret.length() < 32) {
            throw new IllegalStateException("notifications.provider-callback-secret must be at least 32 characters");
        }
        this.queries = queries;
        this.recordDeliveryStatus = recordDeliveryStatus;
        this.callbackSecret = callbackSecret.getBytes(StandardCharsets.UTF_8);
    }

    /** In-app message as the customer sees it. */
    public record InboxItem(UUID id, UUID claimId, String claimNumber, String type, String title, String message,
                            Instant at, boolean read) {
        static InboxItem of(Notification n) {
            return new InboxItem(n.id(), n.claimId(), n.claimNumber(), n.eventType(), n.subject(), n.body(), n.createdAt(),
                    n.readAt() != null);
        }
    }

    /** Delivery log entry as staff see it (address masked). */
    public record DeliveryItem(UUID id, String recipientId, String audience, String channel, String address, String language,
                               String type, String subject, String status, int attempts, String lastError, Instant sentAt,
                               Instant readAt) {
        static DeliveryItem of(Notification n) {
            return new DeliveryItem(n.id(), n.recipientId(), n.audience().name(), n.channel().name(), mask(n.address()),
                    n.language(), n.eventType(), n.subject(), n.status().name(), n.attempts(), n.lastError(), n.sentAt(), n.readAt());
        }
    }

    public record ItemList<T>(List<T> items) {
    }

    public record PreferenceView(String language, ChannelSetting email, ChannelSetting sms, ChannelSetting push) {
        static PreferenceView of(Preference p) {
            return new PreferenceView(p.language(), new ChannelSetting(p.emailEnabled(), p.email()),
                    new ChannelSetting(p.smsEnabled(), p.phone()), new ChannelSetting(p.pushEnabled(), null));
        }
    }

    public record ChannelSetting(boolean enabled, @Size(max = 200) String address) {
    }

    public record PreferenceRequest(@NotBlank String language, @NotNull @Valid ChannelSetting email,
                                    @NotNull @Valid ChannelSetting sms, @NotNull @Valid ChannelSetting push) {
    }

    public record TemplateRequest(@NotBlank @Size(max = 200) String subject, @NotBlank @Size(max = 4000) String body) {
    }

    public record DeliveryStatusRequest(@NotBlank String providerMessageId, @NotNull Notification.Status status) {
    }

    /** N1. */
    @GetMapping("/claimant/v1/notifications")
    public ItemList<InboxItem> inbox(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return new ItemList<>(queries.inbox(jwt.getSubject(), unreadOnly).stream().map(InboxItem::of).toList());
    }

    /** N2. */
    @PostMapping("/claimant/v1/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        queries.markRead(jwt.getSubject(), id);
    }

    /** N3. */
    @GetMapping("/claimant/v1/notification-preferences")
    public PreferenceView preferences(@AuthenticationPrincipal Jwt jwt) {
        return PreferenceView.of(queries.preferences(jwt.getSubject()));
    }

    /** N4. */
    @PutMapping("/claimant/v1/notification-preferences")
    public PreferenceView updatePreferences(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferenceRequest body) {
        Preference preference;
        try {
            preference = new Preference(jwt.getSubject(), body.language(), body.email().address(), body.email().enabled(),
                    body.sms().address(), body.sms().enabled(), body.push().enabled());
        } catch (IllegalArgumentException ex) {
            throw new NotificationException(NotificationException.Code.VALIDATION_FAILED, ex.getMessage());
        }
        return PreferenceView.of(queries.updatePreferences(preference));
    }

    /** N5. */
    @GetMapping("/staff/v1/claims/{claimId}/notifications")
    public ItemList<DeliveryItem> forClaim(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
        return new ItemList<>(queries.forClaim(claimId, PlatformUser.from(jwt).markets()).stream().map(DeliveryItem::of).toList());
    }

    /** N6. */
    @GetMapping("/admin/v1/templates")
    public ItemList<Template> templates() {
        return new ItemList<>(queries.templates());
    }

    /** N6. */
    @PutMapping("/admin/v1/templates/{id}")
    public Template updateTemplate(@PathVariable String id, @Valid @RequestBody TemplateRequest body) {
        return queries.updateTemplate(id, body.subject(), body.body());
    }

    /** N7: provider delivery receipt, signed {@code X-Signature: sha256=<HMAC of raw body>}. */
    @PostMapping("/internal/v1/providers/{provider}/delivery-status")
    public DeliveryItem deliveryStatus(@PathVariable String provider,
                                       @RequestHeader(value = "X-Signature", required = false) String signature,
                                       @RequestBody String rawBody) {
        if (signature == null || !MessageDigest.isEqual(("sha256=" + sign(callbackSecret, rawBody)).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new NotificationException(NotificationException.Code.INVALID_SIGNATURE, "Missing or invalid X-Signature");
        }
        DeliveryStatusRequest body;
        try {
            body = new com.fasterxml.jackson.databind.ObjectMapper().readValue(rawBody, DeliveryStatusRequest.class);
        } catch (Exception ex) {
            throw new NotificationException(NotificationException.Code.VALIDATION_FAILED, "Body is not a delivery status");
        }
        return DeliveryItem.of(recordDeliveryStatus.record(provider, body.providerMessageId(), body.status()));
    }

    public static String sign(byte[] secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String mask(String address) {
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
