package com.claims.platform.insights.application;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.insights.application.InsightsQueries.AlertCandidate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every 5 minutes: claims about to breach (SlaAtRisk) or past (SlaBreached) their SLA are announced once per
 * due date on insights.alerts (Notifications warns the handler and team lead). The event id is derived from
 * claim + kind + due date, so a re-send after a crash is recognisably the same alert to consumers.
 */
public class CheckSlaAlerts {
    public interface AlertPublisher {
        void publish(EventEnvelope alert);
    }

    public interface AlertLog {
        void recordSent(UUID claimId, String kind, Instant slaDueAt);
    }

    private static final int BATCH = 500;

    private final InsightsQueries queries;
    private final AlertPublisher publisher;
    private final AlertLog log;
    private final Clock clock;
    private final Duration atRiskWindow;

    public CheckSlaAlerts(InsightsQueries queries, AlertPublisher publisher, AlertLog log, Clock clock, Duration atRiskWindow) {
        this.queries = queries;
        this.publisher = publisher;
        this.log = log;
        this.clock = clock;
        this.atRiskWindow = atRiskWindow;
    }

    public record Result(int atRisk, int breached) {
    }

    public Result runOnce() {
        Instant now = clock.instant();
        int breached = send(queries.alertCandidates(now, "BREACHED", now, BATCH), "BREACHED", now);
        int atRisk = send(queries.alertCandidates(now.plus(atRiskWindow), "AT_RISK", now, BATCH), "AT_RISK", now);
        return new Result(atRisk, breached);
    }

    private int send(Iterable<AlertCandidate> candidates, String kind, Instant now) {
        int sent = 0;
        for (AlertCandidate c : candidates) {
            if ("AT_RISK".equals(kind) && !c.slaDueAt().isAfter(now)) {
                continue; // already breached: the BREACHED pass covers it
            }
            publisher.publish(alert(c, kind, now));
            log.recordSent(c.claimId(), kind, c.slaDueAt());
            sent++;
        }
        return sent;
    }

    private static EventEnvelope alert(AlertCandidate c, String kind, Instant now) {
        String type = "BREACHED".equals(kind) ? EventTypes.SLA_BREACHED : EventTypes.SLA_AT_RISK;
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
