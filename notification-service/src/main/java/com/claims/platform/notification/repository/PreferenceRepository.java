package com.claims.platform.notification.repository;

import com.claims.platform.notification.entity.Preference;

import java.util.Optional;

/** Customers' notification preferences. */
public interface PreferenceRepository {
    Optional<Preference> findByUserId(String userId);

    void save(Preference preference);
}
