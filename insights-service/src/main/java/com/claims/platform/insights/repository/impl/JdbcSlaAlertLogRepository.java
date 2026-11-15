package com.claims.platform.insights.repository.impl;

import com.claims.platform.insights.repository.SlaAlertLogRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

import static com.claims.platform.insights.repository.impl.JdbcClaimSummaryRepository.ts;

@Repository
public class JdbcSlaAlertLogRepository implements SlaAlertLogRepository {
    private final JdbcClient jdbc;

    public JdbcSlaAlertLogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recordAlertSent(UUID claimId, String kind, Instant slaDueAt) {
        jdbc.sql("INSERT INTO insights.sla_alert (claim_id, kind, sla_due_at) VALUES (:id, :kind, :due) ON CONFLICT DO NOTHING")
                .param("id", claimId).param("kind", kind).param("due", ts(slaDueAt)).update();
    }
}
