package com.claims.platform.notification.service;

import com.claims.platform.notification.entity.Preference;

/** A customer's language and channel preferences. */
public interface NotificationPreferenceService {
    /** The stored preference, or the defaults when the user never saved one. */
    Preference getPreference(String userId);

    Preference updatePreference(Preference preference);
}
