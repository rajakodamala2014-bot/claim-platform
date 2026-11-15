package com.claims.platform.notification.service.impl;

import com.claims.platform.notification.entity.Audience;
import com.claims.platform.notification.entity.Channel;
import com.claims.platform.notification.entity.Notification;
import com.claims.platform.notification.entity.OutboundMessage;
import com.claims.platform.notification.producer.NotificationSender;
import com.claims.platform.notification.repository.NotificationRepository;
import com.claims.platform.notification.service.NotificationDispatchService;
import com.claims.platform.notification.service.NotificationDispatchService.DispatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final int MAX_ATTEMPTS = 3;

    @Mock
    NotificationRepository notifications;
    @Mock
    NotificationSender emailSender;

    NotificationDispatchServiceImpl service;

    @BeforeEach
    void setUp() {
        when(emailSender.channel()).thenReturn(Channel.EMAIL);
        service = new NotificationDispatchServiceImpl(notifications, List.of(emailSender), MAX_ATTEMPTS,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void inAppNotificationsAreSentByBeingPlacedInTheInbox() {
        due(pending(Channel.IN_APP, "cust-priya"));

        DispatchResult result = service.dispatchPendingNotifications();

        assertThat(result).isEqualTo(new DispatchResult(1, 0, 0));
        Notification saved = updated();
        assertThat(saved.status()).isEqualTo(Notification.Status.SENT);
        assertThat(saved.provider()).isEqualTo(NotificationDispatchService.INBOX);
        verify(emailSender, never()).send(any());
    }

    @Test
    void otherChannelsGoThroughTheSenderOfThatChannel() {
        Notification email = pending(Channel.EMAIL, "priya@example.com");
        due(email);
        when(emailSender.providerName()).thenReturn("fake-email");
        when(emailSender.send(any())).thenReturn("msg-1");

        DispatchResult result = service.dispatchPendingNotifications();

        assertThat(result).isEqualTo(new DispatchResult(1, 0, 0));
        ArgumentCaptor<OutboundMessage> message = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(emailSender).send(message.capture());
        assertThat(message.getValue()).isEqualTo(new OutboundMessage(email.id(), Channel.EMAIL, "priya@example.com",
                "subject", "body"));
        Notification saved = updated();
        assertThat(saved.provider()).isEqualTo("fake-email");
        assertThat(saved.providerMessageId()).isEqualTo("msg-1");
        assertThat(saved.sentAt()).isEqualTo(NOW);
    }

    @Test
    void aProviderErrorIsRetriedLaterWithBackoff() {
        due(pending(Channel.EMAIL, "fail@example.com"));
        when(emailSender.send(any())).thenThrow(new IllegalStateException("provider down"));

        DispatchResult result = service.dispatchPendingNotifications();

        assertThat(result).isEqualTo(new DispatchResult(0, 1, 0));
        Notification saved = updated();
        assertThat(saved.status()).isEqualTo(Notification.Status.PENDING);
        assertThat(saved.attempts()).isEqualTo(1);
        assertThat(saved.lastError()).isEqualTo("provider down");
        assertThat(saved.nextAttemptAt()).isAfter(NOW);
    }

    @Test
    void theLastAllowedAttemptFailingMarksTheNotificationFailed() {
        Notification twiceFailed = pending(Channel.EMAIL, "fail@example.com")
                .attemptFailed("provider down", MAX_ATTEMPTS, NOW)
                .attemptFailed("provider down", MAX_ATTEMPTS, NOW);
        due(twiceFailed);
        when(emailSender.send(any())).thenThrow(new IllegalStateException("provider down"));

        DispatchResult result = service.dispatchPendingNotifications();

        assertThat(result).isEqualTo(new DispatchResult(0, 0, 1));
        assertThat(updated().status()).isEqualTo(Notification.Status.FAILED);
    }

    @Test
    void aChannelWithoutASenderCountsAsAFailedAttempt() {
        due(pending(Channel.SMS, "+6591230000"));

        DispatchResult result = service.dispatchPendingNotifications();

        assertThat(result).isEqualTo(new DispatchResult(0, 1, 0));
        assertThat(updated().lastError()).contains("No provider for SMS");
    }

    private void due(Notification... due) {
        when(notifications.findDueForDispatch(NOW, 100)).thenReturn(List.of(due));
    }

    private Notification updated() {
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).update(saved.capture());
        return saved.getValue();
    }

    private static Notification pending(Channel channel, String address) {
        return Notification.pending("cust-priya", Audience.CUSTOMER, UUID.randomUUID(), "SG-MTR-2026-000001",
                UUID.randomUUID(), "ClaimApproved", channel, address, "en", "subject", "body", NOW);
    }
}
