package com.claims.platform.notification.service;

/**
 * Sends PENDING notifications through the sender for their channel. A provider error is retried with backoff
 * up to the configured number of attempts (3), then the notification is logged FAILED. In-app notifications
 * are "sent" by being placed in the inbox. Runs inside the caller's transaction (rows are locked while sending).
 */
public interface NotificationDispatchService {
    /** Provider name recorded for in-app notifications. */
    String INBOX = "inbox";

    DispatchResult dispatchPendingNotifications();

    record DispatchResult(int sent, int retrying, int failed) {
    }
}
