package com.claims.platform.notification.service.impl;

import com.claims.platform.notification.entity.Audience;
import com.claims.platform.notification.entity.Channel;
import com.claims.platform.notification.entity.Notification;
import com.claims.platform.notification.entity.Preference;
import com.claims.platform.notification.entity.Template;
import com.claims.platform.notification.exception.NotificationException;
import com.claims.platform.notification.repository.NotificationRepository;
import com.claims.platform.notification.repository.PreferenceRepository;
import com.claims.platform.notification.repository.TemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The three small services around reference data and delivery receipts. */
@ExtendWith(MockitoExtension.class)
class NotificationTemplateAndDeliveryServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final Template TEMPLATE = new Template("t-1", "ClaimApproved", Audience.CUSTOMER, Channel.EMAIL, "en",
            "Old subject", "Old body");

    @Mock
    TemplateRepository templates;
    @Mock
    PreferenceRepository preferences;
    @Mock
    NotificationRepository notifications;

    // ------------------------------------------------------------------ templates

    @Test
    void updatingATemplateRewordsItAndReturnsTheStoredVersion() {
        Template reworded = new Template("t-1", "ClaimApproved", Audience.CUSTOMER, Channel.EMAIL, "en", "New", "New body");
        when(templates.findById("t-1")).thenReturn(Optional.of(TEMPLATE), Optional.of(reworded));

        Template result = new NotificationTemplateServiceImpl(templates).updateTemplate("t-1", "New", "New body");

        verify(templates).updateWording("t-1", "New", "New body");
        assertThat(result).isEqualTo(reworded);
    }

    @Test
    void anUnknownTemplateIsNotFoundAndNothingIsWritten() {
        when(templates.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new NotificationTemplateServiceImpl(templates).updateTemplate("nope", "New", "New body"))
                .isInstanceOfSatisfying(NotificationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(NotificationException.Code.NOT_FOUND));
        verify(templates, never()).updateWording(any(), any(), any());
    }

    @Test
    void blankWordingIsRejectedBeforeAnythingIsRead() {
        assertThatThrownBy(() -> new NotificationTemplateServiceImpl(templates).updateTemplate("t-1", " ", "body"))
                .isInstanceOfSatisfying(NotificationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(NotificationException.Code.VALIDATION_FAILED));
        verify(templates, never()).findById(any());
    }

    // ------------------------------------------------------------------ preferences

    @Test
    void aUserWithoutSavedPreferencesGetsTheDefaults() {
        when(preferences.findByUserId("cust-new")).thenReturn(Optional.empty());

        assertThat(new NotificationPreferenceServiceImpl(preferences).getPreference("cust-new"))
                .isEqualTo(Preference.defaults("cust-new"));
    }

    @Test
    void updatingAPreferenceSavesAndReturnsIt() {
        Preference preference = Preference.defaults("cust-priya");

        assertThat(new NotificationPreferenceServiceImpl(preferences).updatePreference(preference)).isSameAs(preference);
        verify(preferences).save(preference);
    }

    // ------------------------------------------------------------------ delivery receipts

    @Test
    void aDeliveryReceiptMovesASentMessageToDelivered() {
        Notification sent = pendingEmail().sent("fake-email", "msg-1", NOW);
        when(notifications.findByProviderMessageId("fake-email", "msg-1")).thenReturn(Optional.of(sent));

        Notification result = new DeliveryStatusServiceImpl(notifications)
                .recordDeliveryStatus("fake-email", "msg-1", Notification.Status.DELIVERED);

        assertThat(result.status()).isEqualTo(Notification.Status.DELIVERED);
        verify(notifications).update(result);
    }

    @Test
    void aReceiptForAMessageTheProviderNeverAcceptedIsNotFound() {
        when(notifications.findByProviderMessageId("fake-email", "ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeliveryStatusServiceImpl(notifications)
                .recordDeliveryStatus("fake-email", "ghost", Notification.Status.DELIVERED))
                .isInstanceOfSatisfying(NotificationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(NotificationException.Code.NOT_FOUND));
    }

    @Test
    void onlyDeliveredOrBouncedCanBeReported() {
        Notification sent = pendingEmail().sent("fake-email", "msg-1", NOW);
        when(notifications.findByProviderMessageId("fake-email", "msg-1")).thenReturn(Optional.of(sent));

        assertThatThrownBy(() -> new DeliveryStatusServiceImpl(notifications)
                .recordDeliveryStatus("fake-email", "msg-1", Notification.Status.FAILED))
                .isInstanceOfSatisfying(NotificationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(NotificationException.Code.VALIDATION_FAILED));
        verify(notifications, never()).update(any());
    }

    private static Notification pendingEmail() {
        return Notification.pending("cust-priya", Audience.CUSTOMER, UUID.randomUUID(), "SG-MTR-2026-000001",
                UUID.randomUUID(), "ClaimApproved", Channel.EMAIL, "priya@example.com", "en", "subject", "body", NOW);
    }
}
