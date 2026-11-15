package com.claims.platform.notification.service.impl;

import com.claims.platform.notification.entity.Audience;
import com.claims.platform.notification.entity.Channel;
import com.claims.platform.notification.entity.ClaimContact;
import com.claims.platform.notification.entity.Notification;
import com.claims.platform.notification.exception.NotificationException;
import com.claims.platform.notification.repository.ClaimContactRepository;
import com.claims.platform.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final UUID CLAIM = UUID.randomUUID();

    @Mock
    NotificationRepository notifications;
    @Mock
    ClaimContactRepository contacts;

    NotificationInboxServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationInboxServiceImpl(notifications, contacts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listsAtMostFiftyOfTheUsersOwnNotifications() {
        Notification notification = inApp("cust-priya");
        when(notifications.findInbox("cust-priya", true, 50)).thenReturn(List.of(notification));

        assertThat(service.listInboxNotifications("cust-priya", true)).containsExactly(notification);
    }

    @Test
    void marksTheUsersOwnNotificationReadAtTheCurrentTime() {
        UUID id = UUID.randomUUID();
        when(notifications.markRead(id, "cust-priya", NOW)).thenReturn(true);

        service.markNotificationRead("cust-priya", id);

        verify(notifications).markRead(id, "cust-priya", NOW);
    }

    @Test
    void someoneElsesNotificationLooksLikeItDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(notifications.markRead(id, "cust-tan", NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.markNotificationRead("cust-tan", id))
                .isInstanceOfSatisfying(NotificationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(NotificationException.Code.NOT_FOUND));
    }

    @Test
    void staffSeeTheDeliveryLogOfAClaimInTheirMarket() {
        Notification notification = inApp("cust-priya");
        when(contacts.findByClaimId(CLAIM)).thenReturn(Optional.of(contactIn("SG")));
        when(notifications.findByClaimId(CLAIM)).thenReturn(List.of(notification));

        assertThat(service.listNotificationsOfClaim(CLAIM, Set.of("SG", "MY"))).containsExactly(notification);
    }

    @Test
    void staffOfAnotherMarketAreRefusedBeforeTheLogIsRead() {
        when(contacts.findByClaimId(CLAIM)).thenReturn(Optional.of(contactIn("HK")));

        assertThatThrownBy(() -> service.listNotificationsOfClaim(CLAIM, Set.of("SG")))
                .isInstanceOfSatisfying(NotificationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(NotificationException.Code.FORBIDDEN_MARKET));
        verify(notifications, never()).findByClaimId(CLAIM);
    }

    @Test
    void anUnknownClaimIsNotFound() {
        when(contacts.findByClaimId(CLAIM)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listNotificationsOfClaim(CLAIM, Set.of("SG")))
                .isInstanceOfSatisfying(NotificationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(NotificationException.Code.NOT_FOUND));
    }

    private static ClaimContact contactIn(String market) {
        return new ClaimContact(CLAIM, "SG-MTR-2026-000001", "cust-priya", market, "MOTOR", null, null);
    }

    private static Notification inApp(String recipient) {
        return Notification.pending(recipient, Audience.CUSTOMER, CLAIM, "SG-MTR-2026-000001", UUID.randomUUID(),
                "ClaimSubmitted", Channel.IN_APP, recipient, "en", "subject", "body", NOW);
    }
}
