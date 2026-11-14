package com.claims.platform.claims.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "claim_status_history")
public class ClaimStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID claimId;
    private String fromStatus;
    private String toStatus;
    private String actorId;
    private String reason;
    private boolean visibleToClaimant;
    private Instant createdAt;
}
