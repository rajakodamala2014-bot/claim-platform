package com.claims.platform.insights.application;

import com.claims.platform.insights.application.InsightsQueries.ApprovalRow;
import com.claims.platform.insights.application.InsightsQueries.ClaimRow;
import com.claims.platform.insights.application.InsightsQueries.ExposureRow;
import com.claims.platform.insights.application.InsightsQueries.Page;
import com.claims.platform.insights.application.InsightsQueries.PerformanceRow;
import com.claims.platform.insights.application.InsightsQueries.QueueFilter;
import com.claims.platform.insights.application.InsightsQueries.QueueView;
import com.claims.platform.insights.application.InsightsQueries.WorkloadRow;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * I1-I7: the read-only staff endpoints. This class owns the access rules (markets, team, role); the queries
 * themselves are plain reads of the pre-built read model.
 */
public class StaffInsights {
    private static final int MAX_LIMIT = 100;

    private final InsightsQueries queries;
    private final Clock clock;
    private final Duration atRiskWindow;

    public StaffInsights(InsightsQueries queries, Clock clock, Duration atRiskWindow) {
        this.queries = queries;
        this.clock = clock;
        this.atRiskWindow = atRiskWindow;
    }

    /** I1. */
    public Page workQueue(Viewer viewer, QueueView view, String market, String lineOfBusiness, String cursor, Integer limit) {
        Set<String> markets = viewer.marketsFor(market);
        String team = view == QueueView.TEAM ? viewer.teamFor(null) : null;
        Instant afterDue = null;
        UUID afterId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
                afterDue = Instant.parse(parts[0]);
                afterId = UUID.fromString(parts[1]);
            } catch (RuntimeException ex) {
                throw new InsightsException(InsightsException.Code.VALIDATION_FAILED, "cursor is not valid");
            }
        }
        return queries.workQueue(new QueueFilter(view, markets, lineOfBusiness, viewer.id(), team), afterDue, afterId, limit(limit));
    }

    public static String cursorOf(ClaimRow last) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((last.slaDueAt() + "|" + last.claimId()).getBytes(StandardCharsets.UTF_8));
    }

    /** I2. */
    public List<ClaimRow> search(Viewer viewer, String text, String status, String market, Instant from, Instant to) {
        if ((text == null || text.isBlank()) && status == null && from == null && to == null) {
            throw new InsightsException(InsightsException.Code.VALIDATION_FAILED, "give at least one of q, status, from, to");
        }
        return queries.search(viewer.marketsFor(market), text == null ? null : text.trim(), status, from, to, 50);
    }

    /** I3: team leads see their team's queue, managers every team in their markets. */
    public List<ApprovalRow> approvals(Viewer viewer, String market) {
        requireSupervisor(viewer);
        return queries.approvals(viewer.marketsFor(market), viewer.isManager() ? null : viewer.teamFor(null), viewer.id());
    }

    /** I4. */
    public List<WorkloadRow> workload(Viewer viewer, String team) {
        requireSupervisor(viewer);
        return queries.workload(viewer.markets(), viewer.teamFor(team));
    }

    /** I5. Defaults to the last 30 days. */
    public List<PerformanceRow> performance(Viewer viewer, String team, Instant from, Instant to) {
        requireSupervisor(viewer);
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        if (!start.isBefore(end)) {
            throw new InsightsException(InsightsException.Code.VALIDATION_FAILED, "from must be before to");
        }
        return queries.performance(viewer.markets(), viewer.teamFor(team), start, end);
    }

    /** I6: claims within the at-risk window or already breached. */
    public List<ClaimRow> sla(Viewer viewer, String market) {
        requireSupervisor(viewer);
        return queries.slaWatch(viewer.marketsFor(market), clock.instant().plus(atRiskWindow), 200);
    }

    /** I7. */
    public List<ExposureRow> exposure(Viewer viewer, String market, String lineOfBusiness) {
        requireSupervisor(viewer);
        return queries.exposure(viewer.marketsFor(market), lineOfBusiness);
    }

    public Duration atRiskWindow() {
        return atRiskWindow;
    }

    private static void requireSupervisor(Viewer viewer) {
        if (!viewer.isSupervisor()) {
            throw new InsightsException(InsightsException.Code.FORBIDDEN_ROLE, "Team lead or manager role required");
        }
    }

    private static int limit(Integer limit) {
        int size = limit == null ? 20 : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new InsightsException(InsightsException.Code.VALIDATION_FAILED, "limit must be between 1 and " + MAX_LIMIT);
        }
        return size;
    }
}
