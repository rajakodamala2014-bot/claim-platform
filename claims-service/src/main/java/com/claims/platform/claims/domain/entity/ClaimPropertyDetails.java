package com.claims.platform.claims.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "claim_property_details")
public class ClaimPropertyDetails {
    @Id
    @Column(name = "claim_id")
    private UUID claimId;
    private String propertyAddress;
    private String peril;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] damagedAreas;
    private Boolean habitable;
}
