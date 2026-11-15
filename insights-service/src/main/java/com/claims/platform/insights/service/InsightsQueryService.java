package com.claims.platform.insights.service;

import com.claims.platform.insights.common.StaffViewer;
import com.claims.platform.insights.entity.ApprovalRow;
import com.claims.platform.insights.entity.ClaimRow;
import com.claims.platform.insights.entity.ClaimRowPage;
import com.claims.platform.insights.entity.ExposureRow;
import com.claims.platform.insights.entity.PerformanceRow;
import com.claims.platform.insights.entity.QueueView;
import com.claims.platform.insights.entity.WorkloadRow;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * I1-I7: the read-only staff views. Owns the access rules (markets, team, role); the queries themselves are
 * plain reads of the pre-built read model.
 */
public interface InsightsQueryService {
    /** I1: the viewer's work queue, oldest SLA first. */
    ClaimRowPage getWorkQueue(StaffViewer viewer, QueueView view, String market, String lineOfBusiness, String cursor,
                              Integer limit);

    /** I2: at least one of text, status, from, to is required. */
    List<ClaimRow> searchClaims(StaffViewer viewer, String text, String status, String market, Instant from, Instant to);

    /** I3: team leads see their team's queue, managers every team in their markets. */
    List<ApprovalRow> listPendingApprovals(StaffViewer viewer, String market);

    /** I4. */
    List<WorkloadRow> getWorkload(StaffViewer viewer, String team);

    /** I5: defaults to the last 30 days. */
    List<PerformanceRow> getPerformance(StaffViewer viewer, String team, Instant from, Instant to);

    /** I6: claims within the at-risk window or already breached. */
    List<ClaimRow> listSlaWatch(StaffViewer viewer, String market);

    /** I7. */
    List<ExposureRow> getExposure(StaffViewer viewer, String market, String lineOfBusiness);

    /** How long before its deadline a claim counts as "at risk". */
    Duration atRiskWindow();
}
