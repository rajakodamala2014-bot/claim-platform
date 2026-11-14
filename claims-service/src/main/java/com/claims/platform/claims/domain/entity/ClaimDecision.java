package com.claims.platform.claims.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "decision")
public class ClaimDecision {
    @Id
    private UUID id;
    private UUID claimId;
    private String decision;
    private BigDecimal approvedAmount;
    private String reasonCode;
    private String comment;
    private String decidedBy;
    private Instant decidedAt;
}
