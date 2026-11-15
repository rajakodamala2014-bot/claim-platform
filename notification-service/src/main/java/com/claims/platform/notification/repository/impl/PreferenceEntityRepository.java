package com.claims.platform.notification.repository.impl;

import com.claims.platform.notification.entity.PreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface PreferenceEntityRepository extends JpaRepository<PreferenceEntity, String> {
}
