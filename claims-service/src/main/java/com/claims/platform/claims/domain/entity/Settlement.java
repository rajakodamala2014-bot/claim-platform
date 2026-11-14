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
@Table(name = "settlement")
public class Settlement {
    @Id
    private UUID id;
    private UUID claimId;
    private BigDecimal amount;
    private String currency;
    private String payeeName;
    private String payeeType;
    private String method;
    private String bankDetailsRef;
    private String status;
    private String paymentReference;
    private String approvedBy;
    private Instant paidAt;

    public String currencyCode() {
        return currency == null ? null : currency.trim();
    }
}
