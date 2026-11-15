package com.claims.platform.notification.repository.impl;

import com.claims.platform.notification.entity.StaffMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface StaffMemberEntityRepository extends JpaRepository<StaffMemberEntity, String> {
    List<StaffMemberEntity> findByTeamIdAndRoleOrderByUserId(String teamId, String role);
}
