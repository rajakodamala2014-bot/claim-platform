package com.claims.platform.notification.repository;

import com.claims.platform.notification.entity.StaffMember;

import java.util.List;
import java.util.Optional;

/** Staff directory (seeded reference data, read-only in this service). */
public interface StaffDirectoryRepository {
    Optional<StaffMember> findByUserId(String userId);

    List<StaffMember> findTeamLeadsOfTeam(String teamId);
}
