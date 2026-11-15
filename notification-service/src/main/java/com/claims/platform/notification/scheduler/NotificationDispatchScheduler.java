package com.claims.platform.notification.scheduler;

import com.claims.platform.notification.service.NotificationDispatchService;
import com.claims.platform.notification.service.NotificationDispatchService.DispatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Sends due notifications every second. Rows are claimed with SKIP LOCKED, so instances share the work. */
@Component
@ConditionalOnProperty(name = "notifications.dispatch.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDispatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchScheduler.class);

    private final NotificationDispatchService dispatchService;
    private final TransactionTemplate tx;

    public NotificationDispatchScheduler(NotificationDispatchService dispatchService, TransactionTemplate tx) {
        this.dispatchService = dispatchService;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${notifications.dispatch.interval:1s}")
    void dispatchPendingNotifications() {
        try {
            DispatchResult result = tx.execute(status -> dispatchService.dispatchPendingNotifications());
            if (result != null && result.sent() + result.failed() + result.retrying() > 0) {
                log.info("Dispatched notifications: {}", result);
            }
        } catch (RuntimeException ex) {
            log.warn("Dispatch run failed: {}", ex.getMessage());
        }
    }
}
