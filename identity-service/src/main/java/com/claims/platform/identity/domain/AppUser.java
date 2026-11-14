package com.claims.platform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    private String id;
    private String email;
    private String fullName;
    private String phone;
    private String role;
    private String status;
    private Instant createdAt;
}
