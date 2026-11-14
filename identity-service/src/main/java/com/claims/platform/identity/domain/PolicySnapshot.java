package com.claims.platform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "policy_snapshot")
public class PolicySnapshot {
    @Id
    private String policyId;
    private String policyNumber;
    private String marketCode;
    private String product;
    private String status;
    private LocalDate coverageStart;
    private LocalDate coverageEnd;
    private BigDecimal excess;
    private BigDecimal coverLimit;
    private String currency;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] coveredPerils;
    private String insuredUserId;
    private Instant fetchedAt;
}
