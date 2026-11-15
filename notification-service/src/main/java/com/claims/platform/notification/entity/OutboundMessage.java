package com.claims.platform.notification.entity;

import java.util.UUID;

/** A message about to leave through a provider. */
public record OutboundMessage(UUID notificationId, Channel channel, String address, String subject, String body) {
}
