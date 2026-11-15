package com.claims.platform.insights.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read-side queries over the claim_summary read model. All are market-scoped by the caller. */
public interface InsightsQueries {

    enum QueueView { UNASSIGNED, MINE, TEAM }

    record QueueFilter(QueueView view, Set<String> markets, String lineOfBusiness, String handlerId, String teamId) {
    }

    record ClaimRow(UUID claimId, String claimNumber, String market, String lineOfBusiness, String policyNumber,
                    String status, String coverStatus, String handlerId, String teamId, Instant submittedAt,
                    Instant slaDueAt, boolean slaPaused, boolean slaRunning, long reserveOutstandingMinor, String currency) {
    }

    record Page(List<ClaimRow> items, String nextCursor) {
    }

    /** I1: oldest SLA first, keyset paginated on (sla_due_at, claim_id). */
    Page workQueue(QueueFilter filter, Instant afterDue, UUID afterId, int limit);

    /** I2: claim or policy number (contains, case-insensitive), status, market, submitted between. */
    List<ClaimRow> search(Set<String> markets, String text, String status, Instant from, Instant to, int limit);

    record ApprovalRow(UUID claimId, String claimNumber, String market, String lineOfBusiness, UUID decisionId,
                       String outcome, long amountMinor, String currency, String proposedBy, Instant proposedAt,
                       String teamId) {
    }

    /** I3: decisions awaiting approval, excluding the viewer's own proposals; team = null means any team. */
    List<ApprovalRow> approvals(Set<String> markets, String teamId, String excludeProposer);

    record WorkloadRow(String handlerId, int openClaims, Map<String, Integer> byStatus, Instant oldestSubmittedAt) {
    }

    /** I4: open claims per handler of a team. */
    List<WorkloadRow> workload(Set<String> markets, String teamId);

    record PerformanceRow(String handlerId, int closedClaims, double averageCycleHours, Double slaMetPercent,
                          double reopenRatePercent) {
    }

    /** I5: claims closed in [from, to) per handler of a team. */
    List<PerformanceRow> performance(Set<String> markets, String teamId, Instant from, Instant to);

    /** I6: claims with a running SLA due before {@code dueBefore}, earliest first. */
    List<ClaimRow> slaWatch(Set<String> markets, Instant dueBefore, int limit);

    record ExposureRow(String market, String lineOfBusiness, String currency, int outstandingClaims,
                       long totalReserveMinor) {
    }

    /** I7: open claims and total reserve by market x line of business x currency. */
    List<ExposureRow> exposure(Set<String> markets, String lineOfBusiness);

    record AlertCandidate(UUID claimId, String claimNumber, String market, String lineOfBusiness, String handlerId,
                          String teamId, Instant slaDueAt) {
    }

    /** Running SLAs due before {@code dueBefore} with no alert of {@code kind} for that due date yet. */
    List<AlertCandidate> alertCandidates(Instant dueBefore, String kind, Instant now, int limit);
}
