package com.claims.platform.insights.entity;

import java.time.Instant;
import java.util.UUID;

/** A decision waiting for a team lead or manager. */
public record ApprovalRow(UUID claimId, String claimNumber, String market, String lineOfBusiness, UUID decisionId,
                          String outcome, long amountMinor, String currency, String proposedBy, Instant proposedAt,
                          String teamId) {
}
