package com.claims.platform.notification.infrastructure;

import com.claims.platform.notification.application.NotificationPorts.DeliveryProvider;
import com.claims.platform.notification.application.NotificationPorts.OutboundMessage;
import com.claims.platform.notification.domain.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Stand-ins for the e-mail / SMS / push providers (sync HTTP APIs in production). They log the message (PII
 * masked by the JSON log formatter) and return a provider message id. To exercise retries and failure
 * handling: an e-mail address containing "fail" or a phone number ending in 0000 makes the provider error.
 */
public final class FakeProviders {
    private static final Logger log = LoggerFactory.getLogger(FakeProviders.class);

    private FakeProviders() {
    }

    public static DeliveryProvider email() {
        return new Fake("fake-email", Channel.EMAIL, address -> address != null && address.contains("fail"));
    }

    public static DeliveryProvider sms() {
        return new Fake("fake-sms", Channel.SMS, address -> address != null && address.replace(" ", "").endsWith("0000"));
    }

    public static DeliveryProvider push() {
        return new Fake("fake-push", Channel.PUSH, address -> false);
    }

    private record Fake(String name, Channel channel, java.util.function.Predicate<String> failsFor) implements DeliveryProvider {
        @Override
        public String send(OutboundMessage message) {
            if (failsFor.test(message.address())) {
                throw new IllegalStateException(name + " rejected the message (simulated provider error)");
            }
            String id = name + "-" + UUID.randomUUID();
            log.info("{} sent {} to {}: {}", name, message.notificationId(), message.address(), message.subject());
            return id;
        }
    }
}
