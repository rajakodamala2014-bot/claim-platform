package com.claims.platform.insights.repository;

import com.claims.platform.insights.entity.AlertCandidate;
import com.claims.platform.insights.entity.ApprovalRow;
import com.claims.platform.insights.entity.ClaimRow;
import com.claims.platform.insights.entity.ClaimRowPage;
import com.claims.platform.insights.entity.ExposureRow;
import com.claims.platform.insights.entity.PerformanceRow;
import com.claims.platform.insights.entity.QueueFilter;
import com.claims.platform.insights.entity.WorkloadRow;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Read-side queries over the claim_summary read model. All are market-scoped by the caller. */
public interface InsightsQueryRepository {
    /** I1: oldest SLA first, keyset paginated on (sla_due_at, claim_id). */
    ClaimRowPage findWorkQueue(QueueFilter filter, Instant afterDue, UUID afterId, int limit);

    /** I2: claim or policy number (contains, case-insensitive), status, market, submitted between. */
    List<ClaimRow> searchClaims(Set<String> markets, String text, String status, Instant from, Instant to, int limit);

    /** I3: decisions awaiting approval, excluding the viewer's own proposals; team = null means any team. */
    List<ApprovalRow> findPendingApprovals(Set<String> markets, String teamId, String excludeProposer);

    /** I4: open claims per handler of a team. */
    List<WorkloadRow> findWorkload(Set<String> markets, String teamId);

    /** I5: claims closed in [from, to) per handler of a team. */
    List<PerformanceRow> findPerformance(Set<String> markets, String teamId, Instant from, Instant to);

    /** I6: claims with a running SLA due before {@code dueBefore}, earliest first. */
    List<ClaimRow> findSlaWatch(Set<String> markets, Instant dueBefore, int limit);

    /** I7: open claims and total reserve by market x line of business x currency. */
    List<ExposureRow> findExposure(Set<String> markets, String lineOfBusiness);

    /** Running SLAs due before {@code dueBefore} with no alert of {@code kind} for that due date yet. */
    List<AlertCandidate> findAlertCandidates(Instant dueBefore, String kind, Instant now, int limit);
}
