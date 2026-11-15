package com.claims.platform.insights.service.impl;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.insights.entity.AlertCandidate;
import com.claims.platform.insights.entity.ClaimSummary;
import com.claims.platform.insights.producer.SlaAlertProducer;
import com.claims.platform.insights.repository.ClaimSummaryRepository;
import com.claims.platform.insights.repository.InsightsQueryRepository;
import com.claims.platform.insights.repository.SlaAlertLogRepository;
import com.claims.platform.insights.service.ClaimProjectionService.ProjectionOutcome;
import com.claims.platform.insights.service.SlaAlertService.SlaAlertResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaAlertAndProjectionServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final Duration AT_RISK_WINDOW = Duration.ofHours(4);

    @Mock
    InsightsQueryRepository queries;
    @Mock
    SlaAlertProducer producer;
    @Mock
    SlaAlertLogRepository alertLog;
    @Mock
    ClaimSummaryRepository summaries;

    // ------------------------------------------------------------------ SLA alerts

    @Test
    void breachedAndAtRiskClaimsAreAnnouncedAndRemembered() {
        AlertCandidate breached = candidate(NOW.minus(Duration.ofHours(1)));
        AlertCandidate atRisk = candidate(NOW.plus(Duration.ofHours(2)));
        when(queries.findAlertCandidates(NOW, "BREACHED", NOW, 500)).thenReturn(List.of(breached));
        when(queries.findAlertCandidates(NOW.plus(AT_RISK_WINDOW), "AT_RISK", NOW, 500)).thenReturn(List.of(atRisk));

        SlaAlertResult result = slaAlertService().publishDueSlaAlerts();

        assertThat(result).isEqualTo(new SlaAlertResult(1, 1));
        ArgumentCaptor<EventEnvelope> alerts = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(producer, times(2)).publishAlert(alerts.capture());
        assertThat(alerts.getAllValues()).extracting(EventEnvelope::type)
                .containsExactly(EventTypes.SLA_BREACHED, EventTypes.SLA_AT_RISK);
        EventEnvelope atRiskAlert = alerts.getAllValues().get(1);
        assertThat(atRiskAlert.claimId()).isEqualTo(atRisk.claimId());
        assertThat(atRiskAlert.data()).containsEntry("hoursRemaining", 2L).containsEntry("handlerId", "h-arun");
        verify(alertLog).recordAlertSent(breached.claimId(), "BREACHED", breached.slaDueAt());
        verify(alertLog).recordAlertSent(atRisk.claimId(), "AT_RISK", atRisk.slaDueAt());
    }

    @Test
    void aClaimThatAlreadyBreachedIsNotAlsoAnnouncedAsAtRisk() {
        AlertCandidate breached = candidate(NOW.minus(Duration.ofMinutes(5)));
        when(queries.findAlertCandidates(NOW, "BREACHED", NOW, 500)).thenReturn(List.of());
        when(queries.findAlertCandidates(NOW.plus(AT_RISK_WINDOW), "AT_RISK", NOW, 500)).thenReturn(List.of(breached));

        assertThat(slaAlertService().publishDueSlaAlerts()).isEqualTo(new SlaAlertResult(0, 0));
        verify(producer, never()).publishAlert(any());
    }

    @Test
    void theSameAlertAlwaysGetsTheSameEventId() {
        AlertCandidate atRisk = candidate(NOW.plus(Duration.ofHours(2)));
        when(queries.findAlertCandidates(NOW, "BREACHED", NOW, 500)).thenReturn(List.of());
        when(queries.findAlertCandidates(NOW.plus(AT_RISK_WINDOW), "AT_RISK", NOW, 500)).thenReturn(List.of(atRisk));

        slaAlertService().publishDueSlaAlerts();
        slaAlertService().publishDueSlaAlerts();

        ArgumentCaptor<EventEnvelope> alerts = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(producer, times(2)).publishAlert(alerts.capture());
        assertThat(alerts.getAllValues().get(0).eventId()).isEqualTo(alerts.getAllValues().get(1).eventId());
    }

    // ------------------------------------------------------------------ projection

    @Test
    void aSubmittedClaimCreatesItsSummaryOnce() {
        EventEnvelope submitted = event(EventTypes.CLAIM_SUBMITTED);
        when(summaries.findByClaimId(submitted.claimId())).thenReturn(Optional.empty());

        assertThat(projectionService().projectClaimEvent(submitted)).isEqualTo(ProjectionOutcome.CREATED);
        verify(summaries).save(any(ClaimSummary.class));
    }

    @Test
    void aSecondSubmittedEventDoesNotOverwriteTheSummary() {
        EventEnvelope submitted = event(EventTypes.CLAIM_SUBMITTED);
        when(summaries.findByClaimId(submitted.claimId())).thenReturn(Optional.of(ClaimSummary.fromSubmitted(submitted)));

        assertThat(projectionService().projectClaimEvent(submitted)).isEqualTo(ProjectionOutcome.UPDATED);
        verify(summaries, never()).save(any());
    }

    @Test
    void anEventForAClaimTheReadModelNeverSawIsIgnored() {
        EventEnvelope assigned = event(EventTypes.CLAIM_ASSIGNED);
        when(summaries.findByClaimId(assigned.claimId())).thenReturn(Optional.empty());

        assertThat(projectionService().projectClaimEvent(assigned)).isEqualTo(ProjectionOutcome.IGNORED_UNKNOWN_CLAIM);
        verify(summaries, never()).save(any());
    }

    @Test
    void laterEventsAreFoldedIntoTheSummaryAndSaved() {
        EventEnvelope submitted = event(EventTypes.CLAIM_SUBMITTED);
        ClaimSummary summary = ClaimSummary.fromSubmitted(submitted);
        EventEnvelope assigned = new EventEnvelope(UUID.randomUUID(), EventTypes.CLAIM_ASSIGNED, 1, NOW, submitted.claimId(),
                "SG", "MOTOR", null, Map.of("handlerId", "h-arun", "teamId", "team-sg-motor"));
        when(summaries.findByClaimId(submitted.claimId())).thenReturn(Optional.of(summary));

        assertThat(projectionService().projectClaimEvent(assigned)).isEqualTo(ProjectionOutcome.UPDATED);
        verify(summaries).save(summary);
    }

    private SlaAlertServiceImpl slaAlertService() {
        return new SlaAlertServiceImpl(queries, producer, alertLog, Clock.fixed(NOW, ZoneOffset.UTC), AT_RISK_WINDOW);
    }

    private ClaimProjectionServiceImpl projectionService() {
        return new ClaimProjectionServiceImpl(summaries);
    }

    private static AlertCandidate candidate(Instant slaDueAt) {
        return new AlertCandidate(UUID.randomUUID(), "SG-MTR-2026-000001", "SG", "MOTOR", "h-arun", "team-sg-motor", slaDueAt);
    }

    private static EventEnvelope event(String type) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, NOW, UUID.randomUUID(), "SG", "MOTOR", null,
                Map.of("claimNumber", "SG-MTR-2026-000001", "claimantId", "cust-priya", "policyNumber", "SG-MTR-POL-1001",
                        "slaDueAt", NOW.plus(Duration.ofDays(2)).toString()));
    }
}
