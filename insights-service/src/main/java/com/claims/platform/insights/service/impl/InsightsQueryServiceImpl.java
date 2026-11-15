package com.claims.platform.insights.service.impl;

import com.claims.platform.insights.common.StaffViewer;
import com.claims.platform.insights.common.WorkQueueCursor;
import com.claims.platform.insights.entity.ApprovalRow;
import com.claims.platform.insights.entity.ClaimRow;
import com.claims.platform.insights.entity.ClaimRowPage;
import com.claims.platform.insights.entity.ExposureRow;
import com.claims.platform.insights.entity.PerformanceRow;
import com.claims.platform.insights.entity.QueueFilter;
import com.claims.platform.insights.entity.QueueView;
import com.claims.platform.insights.entity.WorkloadRow;
import com.claims.platform.insights.exception.InsightsException;
import com.claims.platform.insights.repository.InsightsQueryRepository;
import com.claims.platform.insights.service.InsightsQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class InsightsQueryServiceImpl implements InsightsQueryService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int SEARCH_LIMIT = 50;
    private static final int SLA_WATCH_LIMIT = 200;
    private static final Duration DEFAULT_PERFORMANCE_PERIOD = Duration.ofDays(30);

    private final InsightsQueryRepository queries;
    private final Clock clock;
    private final Duration atRiskWindow;

    public InsightsQueryServiceImpl(InsightsQueryRepository queries, Clock clock,
                                    @Value("${insights.sla.at-risk-window:4h}") Duration atRiskWindow) {
        this.queries = queries;
        this.clock = clock;
        this.atRiskWindow = atRiskWindow;
    }

    @Override
    public ClaimRowPage getWorkQueue(StaffViewer viewer, QueueView view, String market, String lineOfBusiness,
                                     String cursor, Integer limit) {
        Set<String> markets = viewer.marketsFor(market);
        String team = view == QueueView.TEAM ? viewer.teamFor(null) : null;
        WorkQueueCursor position = WorkQueueCursor.decode(cursor);
        return queries.findWorkQueue(new QueueFilter(view, markets, lineOfBusiness, viewer.id(), team),
                position.afterDue(), position.afterId(), validatedLimit(limit));
    }

    @Override
    public List<ClaimRow> searchClaims(StaffViewer viewer, String text, String status, String market, Instant from, Instant to) {
        if ((text == null || text.isBlank()) && status == null && from == null && to == null) {
            throw new InsightsException(InsightsException.Code.VALIDATION_FAILED, "give at least one of q, status, from, to");
        }
        return queries.searchClaims(viewer.marketsFor(market), text == null ? null : text.trim(), status, from, to, SEARCH_LIMIT);
    }

    @Override
    public List<ApprovalRow> listPendingApprovals(StaffViewer viewer, String market) {
        requireSupervisor(viewer);
        return queries.findPendingApprovals(viewer.marketsFor(market), viewer.isManager() ? null : viewer.teamFor(null),
                viewer.id());
    }

    @Override
    public List<WorkloadRow> getWorkload(StaffViewer viewer, String team) {
        requireSupervisor(viewer);
        return queries.findWorkload(viewer.markets(), viewer.teamFor(team));
    }

    @Override
    public List<PerformanceRow> getPerformance(StaffViewer viewer, String team, Instant from, Instant to) {
        requireSupervisor(viewer);
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(DEFAULT_PERFORMANCE_PERIOD) : from;
        if (!start.isBefore(end)) {
            throw new InsightsException(InsightsException.Code.VALIDATION_FAILED, "from must be before to");
        }
        return queries.findPerformance(viewer.markets(), viewer.teamFor(team), start, end);
    }

    @Override
    public List<ClaimRow> listSlaWatch(StaffViewer viewer, String market) {
        requireSupervisor(viewer);
        return queries.findSlaWatch(viewer.marketsFor(market), clock.instant().plus(atRiskWindow), SLA_WATCH_LIMIT);
    }

    @Override
    public List<ExposureRow> getExposure(StaffViewer viewer, String market, String lineOfBusiness) {
        requireSupervisor(viewer);
        return queries.findExposure(viewer.marketsFor(market), lineOfBusiness);
    }

    @Override
    public Duration atRiskWindow() {
        return atRiskWindow;
    }

    private static void requireSupervisor(StaffViewer viewer) {
        if (!viewer.isSupervisor()) {
            throw new InsightsException(InsightsException.Code.FORBIDDEN_ROLE, "Team lead or manager role required");
        }
    }

    private static int validatedLimit(Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new InsightsException(InsightsException.Code.VALIDATION_FAILED, "limit must be between 1 and " + MAX_LIMIT);
        }
        return size;
    }
}
