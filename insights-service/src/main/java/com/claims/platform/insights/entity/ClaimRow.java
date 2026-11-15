package com.claims.platform.insights.entity;

import java.time.Instant;
import java.util.UUID;

/** One claim as the read model lists it in queues, search results and the SLA watch. */
public record ClaimRow(UUID claimId, String claimNumber, String market, String lineOfBusiness, String policyNumber,
                       String status, String coverStatus, String handlerId, String teamId, Instant submittedAt,
                       Instant slaDueAt, boolean slaPaused, boolean slaRunning, long reserveOutstandingMinor, String currency) {
}
