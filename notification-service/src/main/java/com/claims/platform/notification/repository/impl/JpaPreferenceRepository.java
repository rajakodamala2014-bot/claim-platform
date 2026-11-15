package com.claims.platform.notification.repository.impl;

import com.claims.platform.notification.entity.Preference;
import com.claims.platform.notification.entity.PreferenceEntity;
import com.claims.platform.notification.repository.PreferenceRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** JPA-backed customer preferences: plain one-table CRUD. Rows are mapped to the {@link Preference} record here. */
@Repository
public class JpaPreferenceRepository implements PreferenceRepository {
    private final PreferenceEntityRepository preferences;

    JpaPreferenceRepository(PreferenceEntityRepository preferences) {
        this.preferences = preferences;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Preference> findByUserId(String userId) {
        return preferences.findById(userId).map(PreferenceEntity::toPreference);
    }

    @Override
    @Transactional
    public void save(Preference preference) {
        PreferenceEntity entity = preferences.findById(preference.userId())
                .orElseGet(() -> new PreferenceEntity(preference.userId()));
        entity.copyFrom(preference);
        preferences.save(entity);
    }
}
