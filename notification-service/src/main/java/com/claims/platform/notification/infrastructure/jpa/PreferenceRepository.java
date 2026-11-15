package com.claims.platform.notification.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

interface PreferenceRepository extends JpaRepository<PreferenceEntity, String> {
}
