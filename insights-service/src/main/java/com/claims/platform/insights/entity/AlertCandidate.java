package com.claims.platform.insights.entity;

import java.time.Instant;
import java.util.UUID;

/** A claim whose SLA is about to be, or has been, breached and that has not been announced yet. */
public record AlertCandidate(UUID claimId, String claimNumber, String market, String lineOfBusiness, String handlerId,
                             String teamId, Instant slaDueAt) {
}
