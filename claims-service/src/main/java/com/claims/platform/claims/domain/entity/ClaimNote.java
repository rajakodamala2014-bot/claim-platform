package com.claims.platform.claims.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "claim_note")
public class ClaimNote {
    @Id
    private UUID id;
    private UUID claimId;
    private String authorId;
    private String body;
    private boolean internal;
    private Instant createdAt;
}
