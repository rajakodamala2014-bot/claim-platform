package com.claims.platform.notification.service.impl;

import com.claims.platform.notification.entity.Preference;
import com.claims.platform.notification.repository.PreferenceRepository;
import com.claims.platform.notification.service.NotificationPreferenceService;
import org.springframework.stereotype.Service;

/** N3, N4. */
@Service
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {
    private final PreferenceRepository preferences;

    public NotificationPreferenceServiceImpl(PreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Override
    public Preference getPreference(String userId) {
        return preferences.findByUserId(userId).orElse(Preference.defaults(userId));
    }

    @Override
    public Preference updatePreference(Preference preference) {
        preferences.save(preference);
        return preference;
    }
}
