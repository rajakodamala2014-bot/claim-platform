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
@Table(name = "claim_motor_details")
public class ClaimMotorDetails {
    @Id
    @Column(name = "claim_id")
    private UUID claimId;
    private String vehicleRegistration;
    private String make;
    private String model;
    private String driverName;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ThirdParty thirdParty;
    private String policeReportNumber;
}
