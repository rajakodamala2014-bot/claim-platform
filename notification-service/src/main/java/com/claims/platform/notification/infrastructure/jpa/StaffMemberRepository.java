package com.claims.platform.notification.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface StaffMemberRepository extends JpaRepository<StaffMemberEntity, String> {
    List<StaffMemberEntity> findByTeamIdAndRoleOrderByUserId(String teamId, String role);
}
