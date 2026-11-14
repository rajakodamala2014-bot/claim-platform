package com.claims.platform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_credential")
public class UserCredential {
    @Id
    @Column(name = "user_id")
    private String userId;
    private String passwordHash;
}
