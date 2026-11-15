package com.claims.platform.notification.producer;

import com.claims.platform.notification.entity.Channel;
import com.claims.platform.notification.entity.OutboundMessage;

/** Sends a message through an e-mail / SMS / push provider (a synchronous HTTP API in real life). */
public interface NotificationSender {
    String providerName();

    Channel channel();

    /** @return the provider's message id; throws when the provider rejects the message */
    String send(OutboundMessage message);
}
