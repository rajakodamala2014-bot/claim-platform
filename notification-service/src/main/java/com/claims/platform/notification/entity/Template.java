package com.claims.platform.notification.entity;

/** A message template; {@code channel} null means it applies to any channel. */
public record Template(String id, String eventType, Audience audience, Channel channel, String language,
                       String subject, String body) {
}
