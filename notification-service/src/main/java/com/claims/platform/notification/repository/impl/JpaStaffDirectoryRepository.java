package com.claims.platform.notification.repository.impl;

import com.claims.platform.notification.entity.StaffMember;
import com.claims.platform.notification.entity.StaffMemberEntity;
import com.claims.platform.notification.repository.StaffDirectoryRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** JPA-backed staff directory. Rows are mapped to the {@link StaffMember} record here. */
@Repository
public class JpaStaffDirectoryRepository implements StaffDirectoryRepository {
    private static final String TEAM_LEAD = "TEAM_LEAD";

    private final StaffMemberEntityRepository staff;

    JpaStaffDirectoryRepository(StaffMemberEntityRepository staff) {
        this.staff = staff;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffMember> findByUserId(String userId) {
        return staff.findById(userId).map(StaffMemberEntity::toStaffMember);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffMember> findTeamLeadsOfTeam(String teamId) {
        return staff.findByTeamIdAndRoleOrderByUserId(teamId, TEAM_LEAD).stream()
                .map(StaffMemberEntity::toStaffMember).toList();
    }
}
