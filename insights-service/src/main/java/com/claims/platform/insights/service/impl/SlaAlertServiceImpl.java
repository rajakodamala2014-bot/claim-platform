package com.claims.platform.insights.service.impl;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.insights.entity.AlertCandidate;
import com.claims.platform.insights.producer.SlaAlertProducer;
import com.claims.platform.insights.repository.InsightsQueryRepository;
import com.claims.platform.insights.repository.SlaAlertLogRepository;
import com.claims.platform.insights.service.SlaAlertService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SlaAlertServiceImpl implements SlaAlertService {
    private static final String BREACHED = "BREACHED";
    private static final String AT_RISK = "AT_RISK";
    private static final int BATCH = 500;

    private final InsightsQueryRepository queries;
    private final SlaAlertProducer producer;
    private final SlaAlertLogRepository alertLog;
    private final Clock clock;
    private final Duration atRiskWindow;

    public SlaAlertServiceImpl(InsightsQueryRepository queries, SlaAlertProducer producer, SlaAlertLogRepository alertLog,
                               Clock clock, @Value("${insights.sla.at-risk-window:4h}") Duration atRiskWindow) {
        this.queries = queries;
        this.producer = producer;
        this.alertLog = alertLog;
        this.clock = clock;
        this.atRiskWindow = atRiskWindow;
    }

    @Override
    public SlaAlertResult publishDueSlaAlerts() {
        Instant now = clock.instant();
        int breached = publishAlerts(queries.findAlertCandidates(now, BREACHED, now, BATCH), BREACHED, now);
        int atRisk = publishAlerts(queries.findAlertCandidates(now.plus(atRiskWindow), AT_RISK, now, BATCH), AT_RISK, now);
        return new SlaAlertResult(atRisk, breached);
    }

    private int publishAlerts(Iterable<AlertCandidate> candidates, String kind, Instant now) {
        int sent = 0;
        for (AlertCandidate candidate : candidates) {
            if (AT_RISK.equals(kind) && !candidate.slaDueAt().isAfter(now)) {
                continue; // already breached: the BREACHED pass covers it
            }
            producer.publishAlert(toAlertEvent(candidate, kind, now));
            alertLog.recordAlertSent(candidate.claimId(), kind, candidate.slaDueAt());
            sent++;
        }
        return sent;
    }

    private static EventEnvelope toAlertEvent(AlertCandidate c, String kind, Instant now) {
        String type = BREACHED.equals(kind) ? EventTypes.SLA_BREACHED : EventTypes.SLA_AT_RISK;
        UUID eventId = UUID.nameUUIDFromBytes((c.claimId() + "|" + kind + "|" + c.slaDueAt()).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("claimNumber", c.claimNumber());
        data.put("slaDueAt", c.slaDueAt().toString());
        data.put("hoursRemaining", Duration.between(now, c.slaDueAt()).toHours());
        if (c.handlerId() != null) {
            data.put("handlerId", c.handlerId());
        }
        if (c.teamId() != null) {
            data.put("teamId", c.teamId());
        }
        return new EventEnvelope(eventId, type, 1, now, c.claimId(), c.market(), c.lineOfBusiness(), null, data);
    }
}
