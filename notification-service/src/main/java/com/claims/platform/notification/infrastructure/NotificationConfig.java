package com.claims.platform.notification.infrastructure;

import com.claims.platform.notification.application.DispatchNotifications;
import com.claims.platform.notification.application.NotificationPorts.ClaimContacts;
import com.claims.platform.notification.application.NotificationPorts.NotificationLog;
import com.claims.platform.notification.application.NotificationPorts.Preferences;
import com.claims.platform.notification.application.NotificationPorts.StaffDirectory;
import com.claims.platform.notification.application.NotificationPorts.Templates;
import com.claims.platform.notification.application.NotificationQueries;
import com.claims.platform.notification.application.PlanNotifications;
import com.claims.platform.notification.application.RecordDeliveryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/** Wires the notification use cases and the fake providers. */
@Configuration
@EnableScheduling
public class NotificationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PlanNotifications planNotifications(ClaimContacts contacts, StaffDirectory staff, Preferences preferences, Templates templates,
                                        NotificationLog log, Clock clock) {
        return new PlanNotifications(contacts, staff, preferences, templates, log, clock);
    }

    @Bean
    DispatchNotifications dispatchNotifications(NotificationLog log, Clock clock,
                                                @Value("${notifications.dispatch.max-attempts:3}") int maxAttempts) {
        return new DispatchNotifications(log, List.of(FakeProviders.email(), FakeProviders.sms(), FakeProviders.push()),
                maxAttempts, clock);
    }

    @Bean
    NotificationQueries notificationQueries(NotificationLog log, Preferences preferences, ClaimContacts contacts,
                                            Templates templates, Clock clock) {
        return new NotificationQueries(log, preferences, contacts, templates, clock);
    }

    @Bean
    RecordDeliveryStatus recordDeliveryStatus(NotificationLog log) {
        return new RecordDeliveryStatus(log);
    }

    /** Sends due notifications every second. Rows are claimed with SKIP LOCKED, so instances share the work. */
    @Component
    @ConditionalOnProperty(name = "notifications.dispatch.enabled", havingValue = "true", matchIfMissing = true)
    static class DispatchScheduler {
        private static final Logger log = LoggerFactory.getLogger(DispatchScheduler.class);
        private final DispatchNotifications dispatch;
        private final TransactionTemplate tx;

        DispatchScheduler(DispatchNotifications dispatch, TransactionTemplate tx) {
            this.dispatch = dispatch;
            this.tx = tx;
        }

        @Scheduled(fixedDelayString = "${notifications.dispatch.interval:1s}")
        void run() {
            try {
                DispatchNotifications.Result result = tx.execute(status -> dispatch.runOnce());
                if (result != null && result.sent() + result.failed() + result.retrying() > 0) {
                    log.info("Dispatched notifications: {}", result);
                }
            } catch (RuntimeException ex) {
                log.warn("Dispatch run failed: {}", ex.getMessage());
            }
        }
    }
}
