package com.claims.platform.insights.repository;

import java.time.Instant;
import java.util.UUID;

/** Remembers which SLA alerts were already published, so each is announced once per due date. */
public interface SlaAlertLogRepository {
    void recordAlertSent(UUID claimId, String kind, Instant slaDueAt);
}
