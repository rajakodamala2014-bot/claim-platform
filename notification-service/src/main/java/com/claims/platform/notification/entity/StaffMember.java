package com.claims.platform.notification.entity;

/** A member of staff as listed in the staff directory. */
public record StaffMember(String userId, String name, String email, String role, String teamId) {
}
