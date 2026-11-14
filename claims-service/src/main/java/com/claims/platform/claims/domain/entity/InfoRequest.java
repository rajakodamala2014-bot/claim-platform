package com.claims.platform.claims.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "info_request")
public class InfoRequest {
    @Id
    private UUID id;
    private UUID claimId;
    private String requestedBy;
    private String message;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] requestedDocTypes;
    private String status;
    private Instant dueAt;
    private Instant fulfilledAt;
    private Instant createdAt;
}
