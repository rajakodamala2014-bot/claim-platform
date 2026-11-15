package com.claims.platform.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Staff directory row (seeded reference data, read-only in this service). */
@Entity
@Table(schema = "notification", name = "staff_directory")
public class StaffMemberEntity {
    @Id
    @Column(name = "user_id")
    private String userId;
    private String name;
    private String email;
    private String role;
    @Column(name = "team_id")
    private String teamId;

    protected StaffMemberEntity() {
    }

    public StaffMember toStaffMember() {
        return new StaffMember(userId, name, email, role, teamId);
    }
}
