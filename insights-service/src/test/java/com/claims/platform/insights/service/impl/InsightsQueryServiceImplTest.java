package com.claims.platform.insights.service.impl;

import com.claims.platform.insights.common.StaffViewer;
import com.claims.platform.insights.common.WorkQueueCursor;
import com.claims.platform.insights.entity.ClaimRow;
import com.claims.platform.insights.entity.ClaimRowPage;
import com.claims.platform.insights.entity.QueueFilter;
import com.claims.platform.insights.entity.QueueView;
import com.claims.platform.insights.exception.InsightsException;
import com.claims.platform.insights.repository.InsightsQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightsQueryServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final Duration AT_RISK_WINDOW = Duration.ofHours(4);
    private static final StaffViewer HANDLER = new StaffViewer("h-arun", "HANDLER", Set.of("SG"), "team-sg-motor");
    private static final StaffViewer TEAM_LEAD = new StaffViewer("tl-meera", "TEAM_LEAD", Set.of("SG"), "team-sg-motor");
    private static final StaffViewer MANAGER = new StaffViewer("m-lee", "MANAGER", Set.of("SG", "HK"), null);

    @Mock
    InsightsQueryRepository queries;

    InsightsQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InsightsQueryServiceImpl(queries, Clock.fixed(NOW, ZoneOffset.UTC), AT_RISK_WINDOW);
    }

    // ------------------------------------------------------------------ I1 work queue

    @Test
    void theQueueIsScopedToTheViewersMarketsAndStartsAtTheBeginningWithoutACursor() {
        ClaimRowPage page = new ClaimRowPage(List.of(), null);
        when(queries.findWorkQueue(new QueueFilter(QueueView.MINE, Set.of("SG"), "MOTOR", "h-arun", null), null, null, 20))
                .thenReturn(page);

        assertThat(service.getWorkQueue(HANDLER, QueueView.MINE, null, "MOTOR", null, null)).isSameAs(page);
    }

    @Test
    void theTeamQueueUsesTheViewersTeamAndACursorContinuesAfterTheLastRow() {
        UUID lastId = UUID.randomUUID();
        Instant lastDue = Instant.parse("2026-03-02T08:00:00Z");
        String cursor = WorkQueueCursor.encode(row(lastId, lastDue));
        ClaimRowPage page = new ClaimRowPage(List.of(), null);
        when(queries.findWorkQueue(new QueueFilter(QueueView.TEAM, Set.of("SG"), null, "h-arun", "team-sg-motor"),
                lastDue, lastId, 50)).thenReturn(page);

        assertThat(service.getWorkQueue(HANDLER, QueueView.TEAM, "sg", null, cursor, 50)).isSameAs(page);
    }

    @Test
    void aMarketTheViewerIsNotAssignedToIsRefused() {
        assertThatThrownBy(() -> service.getWorkQueue(HANDLER, QueueView.MINE, "HK", null, null, null))
                .isInstanceOfSatisfying(InsightsException.class,
                        ex -> assertThat(ex.code()).isEqualTo(InsightsException.Code.FORBIDDEN_MARKET));
        verifyNoInteractions(queries);
    }

    @Test
    void aBrokenCursorOrAnOutOfRangeLimitIsAValidationError() {
        assertThatThrownBy(() -> service.getWorkQueue(HANDLER, QueueView.MINE, null, null, "not-a-cursor", null))
                .isInstanceOfSatisfying(InsightsException.class,
                        ex -> assertThat(ex.code()).isEqualTo(InsightsException.Code.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.getWorkQueue(HANDLER, QueueView.MINE, null, null, null, 101))
                .isInstanceOfSatisfying(InsightsException.class,
                        ex -> assertThat(ex.code()).isEqualTo(InsightsException.Code.VALIDATION_FAILED));
        verifyNoInteractions(queries);
    }

    // ------------------------------------------------------------------ I2 search

    @Test
    void searchNeedsAtLeastOneCriterion() {
        assertThatThrownBy(() -> service.searchClaims(HANDLER, " ", null, null, null, null))
                .isInstanceOfSatisfying(InsightsException.class,
                        ex -> assertThat(ex.code()).isEqualTo(InsightsException.Code.VALIDATION_FAILED));
    }

    @Test
    void searchTrimsTheTextAndReturnsAtMostFiftyClaims() {
        List<ClaimRow> rows = List.of(row(UUID.randomUUID(), NOW));
        when(queries.searchClaims(Set.of("SG"), "POL-1001", "ASSIGNED", null, null, 50)).thenReturn(rows);

        assertThat(service.searchClaims(HANDLER, "  POL-1001 ", "ASSIGNED", null, null, null)).isSameAs(rows);
    }

    // ------------------------------------------------------------------ I3-I7 supervisors only

    @Test
    void handlersCannotSeeApprovalsWorkloadPerformanceSlaOrExposure() {
        assertForbiddenRole(() -> service.listPendingApprovals(HANDLER, null));
        assertForbiddenRole(() -> service.getWorkload(HANDLER, null));
        assertForbiddenRole(() -> service.getPerformance(HANDLER, null, null, null));
        assertForbiddenRole(() -> service.listSlaWatch(HANDLER, null));
        assertForbiddenRole(() -> service.getExposure(HANDLER, null, null));
        verifyNoInteractions(queries);
    }

    @Test
    void teamLeadsSeeTheirOwnTeamsApprovalsAndManagersEveryTeam() {
        service.listPendingApprovals(TEAM_LEAD, null);
        verify(queries).findPendingApprovals(Set.of("SG"), "team-sg-motor", "tl-meera");

        service.listPendingApprovals(MANAGER, "HK");
        verify(queries).findPendingApprovals(Set.of("HK"), null, "m-lee");
    }

    @Test
    void aTeamLeadCannotLookAtAnotherTeamsWorkload() {
        assertForbiddenRole(() -> service.getWorkload(TEAM_LEAD, "team-hk-motor"));
    }

    @Test
    void performanceDefaultsToTheLastThirtyDays() {
        service.getPerformance(TEAM_LEAD, null, null, null);

        verify(queries).findPerformance(Set.of("SG"), "team-sg-motor", NOW.minus(Duration.ofDays(30)), NOW);
    }

    @Test
    void performanceRejectsAPeriodThatEndsBeforeItStarts() {
        assertThatThrownBy(() -> service.getPerformance(TEAM_LEAD, null, NOW, NOW.minusSeconds(1)))
                .isInstanceOfSatisfying(InsightsException.class,
                        ex -> assertThat(ex.code()).isEqualTo(InsightsException.Code.VALIDATION_FAILED));
    }

    @Test
    void theSlaWatchLooksAheadByTheAtRiskWindow() {
        service.listSlaWatch(TEAM_LEAD, null);

        verify(queries).findSlaWatch(Set.of("SG"), NOW.plus(AT_RISK_WINDOW), 200);
    }

    private static void assertForbiddenRole(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(InsightsException.class,
                ex -> assertThat(ex.code()).isEqualTo(InsightsException.Code.FORBIDDEN_ROLE));
    }

    private static ClaimRow row(UUID claimId, Instant slaDueAt) {
        return new ClaimRow(claimId, "SG-MTR-2026-000001", "SG", "MOTOR", "SG-MTR-POL-1001", "ASSIGNED", "CONFIRMED",
                "h-arun", "team-sg-motor", NOW, slaDueAt, false, true, 0, "SGD");
    }
}
