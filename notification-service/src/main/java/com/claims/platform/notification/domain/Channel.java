package com.claims.platform.notification.domain;

public enum Channel {
    /** The app's bell icon. Always on: every recipient gets it. */
    IN_APP,
    EMAIL,
    SMS,
    PUSH
}
