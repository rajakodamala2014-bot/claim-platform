package com.claims.platform.notification.producer.impl;

import com.claims.platform.notification.entity.Channel;
import com.claims.platform.notification.entity.OutboundMessage;
import com.claims.platform.notification.producer.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Stand-ins for the e-mail / SMS / push providers (sync HTTP APIs in production). They log the message (PII
 * masked by the JSON log formatter) and return a provider message id. To exercise retries and failure
 * handling: an e-mail address containing "fail" or a phone number ending in 0000 makes the provider error.
 */
public final class FakeNotificationSenders {
    private static final Logger log = LoggerFactory.getLogger(FakeNotificationSenders.class);

    private FakeNotificationSenders() {
    }

    public static NotificationSender email() {
        return new FakeSender("fake-email", Channel.EMAIL, address -> address != null && address.contains("fail"));
    }

    public static NotificationSender sms() {
        return new FakeSender("fake-sms", Channel.SMS, address -> address != null && address.replace(" ", "").endsWith("0000"));
    }

    public static NotificationSender push() {
        return new FakeSender("fake-push", Channel.PUSH, address -> false);
    }

    private record FakeSender(String providerName, Channel channel, Predicate<String> failsFor) implements NotificationSender {
        @Override
        public String send(OutboundMessage message) {
            if (failsFor.test(message.address())) {
                throw new IllegalStateException(providerName + " rejected the message (simulated provider error)");
            }
            String id = providerName + "-" + UUID.randomUUID();
            log.info("{} sent {} to {}: {}", providerName, message.notificationId(), message.address(), message.subject());
            return id;
        }
    }
}
