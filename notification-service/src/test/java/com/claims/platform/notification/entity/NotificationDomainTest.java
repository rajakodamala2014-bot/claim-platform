package com.claims.platform.notification.entity;

import com.claims.platform.notification.common.NotificationRules;
import com.claims.platform.notification.common.TemplateText;
import com.claims.platform.common.event.EventTypes;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class NotificationDomainTest {
    private static final Instant NOW = Instant.parse("2026-11-14T02:00:00Z");

    @Test
    void whoIsToldFollowsTheDesignTable() {
        assertThat(NotificationRules.audiencesFor(EventTypes.CLAIM_SUBMITTED, Map.of())).containsExactly(Audience.CUSTOMER);
        assertThat(NotificationRules.audiencesFor(EventTypes.PAYMENT_CONFIRMED, Map.of())).containsExactly(Audience.CUSTOMER);
        assertThat(NotificationRules.audiencesFor(EventTypes.INFORMATION_PROVIDED, Map.of())).containsExactly(Audience.HANDLER);
        assertThat(NotificationRules.audiencesFor(EventTypes.PAYMENT_FAILED, Map.of())).containsExactly(Audience.HANDLER);
        assertThat(NotificationRules.audiencesFor(EventTypes.DECISION_PROPOSED, Map.of("approvalRequired", true)))
                .containsExactly(Audience.TEAM_LEAD);
        assertThat(NotificationRules.audiencesFor(EventTypes.DECISION_PROPOSED, Map.of("approvalRequired", false))).isEmpty();
        assertThat(NotificationRules.audiencesFor(EventTypes.SLA_BREACHED, Map.of()))
                .containsExactly(Audience.HANDLER, Audience.TEAM_LEAD);
        assertThat(NotificationRules.audiencesFor(EventTypes.RESERVE_CHANGED, Map.of())).isEmpty();
        assertThat(NotificationRules.audiencesFor(EventTypes.ASSESSMENT_RECORDED, Map.of())).isEmpty();
    }

    @Test
    void decisionOutcomesGoToTheProposerOthersToTheHandler() {
        assertThat(NotificationRules.handlerFor(EventTypes.DECISION_RETURNED, Map.of("proposerId", "h-arun"), "h-other"))
                .isEqualTo("h-arun");
        assertThat(NotificationRules.handlerFor(EventTypes.PAYMENT_FAILED, Map.of("handlerId", "h-kumar"), "h-other"))
                .isEqualTo("h-kumar");
        assertThat(NotificationRules.handlerFor(EventTypes.INFORMATION_PROVIDED, Map.of(), "h-assigned")).isEqualTo("h-assigned");
    }

    @Test
    void templatesRenderPlaceholdersAndBlankUnknownOnes() {
        assertThat(TemplateText.render("Claim {{claimNumber}} paid {{ amount }}. {{unknown}}",
                Map.of("claimNumber", "SG-MTR-2026-000001", "amount", "SGD 4200.00")))
                .isEqualTo("Claim SG-MTR-2026-000001 paid SGD 4200.00.");
        assertThat(TemplateText.render("{{x}}", Map.of("x", "$1 \\ odd"))).isEqualTo("$1 \\ odd");
    }

    @Test
    void preferencesDecideChannelsInAppAlwaysOn() {
        assertThat(Preference.defaults("u").deliveries()).extracting(Preference.Delivery::channel).containsExactly(Channel.IN_APP);
        Preference all = new Preference("u", "ms", "u@example.com", true, "+60123456789", true, true);
        assertThat(all.deliveries()).extracting(Preference.Delivery::channel)
                .containsExactly(Channel.IN_APP, Channel.EMAIL, Channel.SMS, Channel.PUSH);
        assertThatIllegalArgumentException().isThrownBy(() -> new Preference("u", "fr", null, false, null, false, false));
        assertThatIllegalArgumentException().isThrownBy(() -> new Preference("u", "en", "not-an-email", true, null, false, false));
        assertThatIllegalArgumentException().isThrownBy(() -> new Preference("u", "en", null, false, "abc", true, false));
    }

    @Test
    void failedSendsBackOffAndGiveUpAfterTheLastAttempt() {
        Notification n = Notification.pending("cust-priya", Audience.CUSTOMER, UUID.randomUUID(), "SG-1", UUID.randomUUID(),
                EventTypes.CLAIM_SUBMITTED, Channel.SMS, "+6500000000", "en", "s", "b", NOW);

        Notification first = n.attemptFailed("timeout", 3, NOW);
        Notification second = first.attemptFailed("timeout", 3, NOW);
        Notification third = second.attemptFailed("timeout", 3, NOW);

        assertThat(List.of(first.status(), second.status(), third.status()))
                .containsExactly(Notification.Status.PENDING, Notification.Status.PENDING, Notification.Status.FAILED);
        assertThat(first.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofSeconds(2)));
        assertThat(second.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofSeconds(4)));
        assertThat(third.attempts()).isEqualTo(3);
        assertThat(third.lastError()).isEqualTo("timeout");
    }

    @Test
    void deliveryReceiptsOnlyApplyToSentMessages() {
        Notification pending = Notification.pending("u", Audience.CUSTOMER, null, null, UUID.randomUUID(), "X", Channel.EMAIL,
                "u@example.com", "en", "s", "b", NOW);
        Notification sent = pending.sent("fake-email", "m-1", NOW);

        assertThat(sent.deliveryReported(Notification.Status.DELIVERED).status()).isEqualTo(Notification.Status.DELIVERED);
        assertThat(pending.deliveryReported(Notification.Status.DELIVERED).status()).isEqualTo(Notification.Status.PENDING);
        assertThatIllegalArgumentException().isThrownBy(() -> sent.deliveryReported(Notification.Status.FAILED));
    }
}
