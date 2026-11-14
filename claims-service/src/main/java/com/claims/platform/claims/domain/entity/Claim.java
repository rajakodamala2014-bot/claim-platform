package com.claims.platform.claims.domain.entity;

import jakarta.persistence.Column;
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
@Table(name = "claim")
public class Claim {
    @Id
    private UUID id;
    private String claimNumber;
    private String marketCode;
    private String lineOfBusiness;
    private String policyId;
    private String policyNumber;
    private String claimantId;
    private String status;
    private String priority;
    private Instant incidentDate;
    private String incidentLocation;
    private String description;
    private String lossType;
    private String currency;
    private BigDecimal estimatedAmount;
    private BigDecimal reserveAmount;
    private BigDecimal paidAmount;
    private String coverageDecision;
    private Short liabilityPct;
    private String assignedTo;
    private String teamId;
    private boolean fraudFlag;
    private Instant slaDueAt;
    private Instant slaPausedAt;
    private Long slaPausedTotalSec;
    private Instant reportedAt;
    private Instant closedAt;
    private int version;

    public String market() {
        return marketCode == null ? null : marketCode.trim();
    }

    public String currencyCode() {
        return currency == null ? null : currency.trim();
    }
}
