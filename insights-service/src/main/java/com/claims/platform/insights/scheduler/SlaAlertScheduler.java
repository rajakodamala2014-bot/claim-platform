package com.claims.platform.insights.scheduler;

import com.claims.platform.insights.service.SlaAlertService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Checks SLA alerts every 5 minutes; a transaction-scoped advisory lock lets only one instance do it. */
@Component
@ConditionalOnProperty(name = "insights.sla.alerts-enabled", havingValue = "true", matchIfMissing = true)
public class SlaAlertScheduler {
    private static final long LOCK = 0x51A_A1E7L;

    private final SlaAlertService slaAlertService;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public SlaAlertScheduler(SlaAlertService slaAlertService, JdbcClient jdbc, TransactionTemplate tx) {
        this.slaAlertService = slaAlertService;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${insights.sla.alert-check-interval:5m}", initialDelayString = "${insights.sla.alert-check-interval:5m}")
    public void publishDueSlaAlerts() {
        tx.executeWithoutResult(status -> {
            if (Boolean.TRUE.equals(jdbc.sql("SELECT pg_try_advisory_xact_lock(:k)").param("k", LOCK).query(Boolean.class).single())) {
                slaAlertService.publishDueSlaAlerts();
            }
        });
    }
}
