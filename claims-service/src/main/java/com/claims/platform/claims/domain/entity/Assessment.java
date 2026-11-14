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
@Table(name = "assessment")
public class Assessment {
    @Id
    private UUID id;
    private UUID claimId;
    private String assessorId;
    private String coverageDecision;
    private Short liabilityPct;
    private String findings;
    private BigDecimal estimatedLoss;
    private BigDecimal reserve;
    private BigDecimal recommendedPayout;
    private Instant createdAt;
}
