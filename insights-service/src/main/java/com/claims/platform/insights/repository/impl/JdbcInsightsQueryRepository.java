package com.claims.platform.insights.repository.impl;

import com.claims.platform.insights.repository.InsightsQueryRepository;
import com.claims.platform.insights.common.WorkQueueCursor;
import com.claims.platform.insights.entity.AlertCandidate;
import com.claims.platform.insights.entity.ApprovalRow;
import com.claims.platform.insights.entity.ClaimRow;
import com.claims.platform.insights.entity.ClaimRowPage;
import com.claims.platform.insights.entity.ExposureRow;
import com.claims.platform.insights.entity.PerformanceRow;
import com.claims.platform.insights.entity.QueueFilter;
import com.claims.platform.insights.entity.QueueView;
import com.claims.platform.insights.entity.WorkloadRow;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static com.claims.platform.insights.repository.impl.JdbcClaimSummaryRepository.instant;
import static com.claims.platform.insights.repository.impl.JdbcClaimSummaryRepository.ts;

@Repository
public class JdbcInsightsQueryRepository implements InsightsQueryRepository {
    /** Statuses in which someone still has work to do on the claim. */
    private static final String WORKING = "('SUBMITTED','ASSIGNED','UNDER_ASSESSMENT','AWAITING_INFO',"
            + "'DECISION_PENDING','APPROVED','SETTLEMENT_IN_PROGRESS')";
    /** Statuses in which the SLA clock runs (until a decision), unless paused. */
    private static final String SLA_RUNNING = "status IN ('SUBMITTED','ASSIGNED','UNDER_ASSESSMENT','DECISION_PENDING') "
            + "AND NOT sla_paused";
    private static final String ROW_COLUMNS = "claim_id, claim_number, market_code, line_of_business, policy_number, status, "
            + "cover_status, handler_id, team_id, submitted_at, sla_due_at, sla_paused, reserve_outstanding_minor, currency, "
            + "(" + SLA_RUNNING + ") AS sla_running";

    private final JdbcClient jdbc;

    public JdbcInsightsQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ClaimRowPage findWorkQueue(QueueFilter f, Instant afterDue, UUID afterId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + ROW_COLUMNS
                + " FROM insights.claim_summary WHERE market_code = ANY(:markets)");
        Map<String, Object> p = new HashMap<>();
        p.put("markets", markets(f.markets()));
        switch (f.view()) {
            case UNASSIGNED -> sql.append(" AND handler_id IS NULL AND status = 'SUBMITTED'");
            case MINE -> {
                sql.append(" AND handler_id = :me AND status IN ").append(WORKING);
                p.put("me", f.handlerId());
            }
            case TEAM -> {
                sql.append(" AND team_id = :team AND status IN ").append(WORKING);
                p.put("team", f.teamId());
            }
            case PAID -> {
                sql.append(" AND handler_id = :me AND status = 'SETTLED'");
                p.put("me", f.handlerId());
            }
            case REJECTED -> {
                sql.append(" AND handler_id = :me AND status = 'REJECTED'");
                p.put("me", f.handlerId());
            }
        }
        if (f.lineOfBusiness() != null) {
            sql.append(" AND line_of_business = :lob");
            p.put("lob", f.lineOfBusiness());
        }
        if (afterDue != null) {
            sql.append(" AND (sla_due_at, claim_id) > (:afterDue, :afterId)");
            p.put("afterDue", ts(afterDue));
            p.put("afterId", afterId);
        }
        sql.append(" ORDER BY sla_due_at, claim_id LIMIT :limit");
        p.put("limit", limit + 1);
        List<ClaimRow> rows = jdbc.sql(sql.toString()).params(p).query((rs, n) -> row(rs)).list();
        if (rows.size() <= limit) {
            return new ClaimRowPage(rows, null);
        }
        List<ClaimRow> page = rows.subList(0, limit);
        return new ClaimRowPage(page, WorkQueueCursor.encode(page.getLast()));
    }

    @Override
    public List<ClaimRow> searchClaims(Set<String> markets, String text, String status, Instant from, Instant to, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + ROW_COLUMNS + " FROM insights.claim_summary WHERE market_code = ANY(:markets)");
        Map<String, Object> p = new HashMap<>();
        p.put("markets", markets(markets));
        if (text != null && !text.isEmpty()) {
            sql.append(" AND (claim_number ILIKE :text OR policy_number ILIKE :text)");
            p.put("text", "%" + text.replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (status != null) {
            sql.append(" AND status = :status");
            p.put("status", status);
        }
        if (from != null) {
            sql.append(" AND submitted_at >= :from");
            p.put("from", ts(from));
        }
        if (to != null) {
            sql.append(" AND submitted_at < :to");
            p.put("to", ts(to));
        }
        sql.append(" ORDER BY submitted_at DESC, claim_id LIMIT :limit");
        p.put("limit", limit);
        return jdbc.sql(sql.toString()).params(p).query((rs, n) -> row(rs)).list();
    }

    @Override
    public List<ApprovalRow> findPendingApprovals(Set<String> markets, String teamId, String excludeProposer) {
        String sql = """
                SELECT claim_id, claim_number, market_code, line_of_business, pending_decision_id, pending_decision_outcome,
                       pending_decision_amount_minor, currency, pending_decision_proposed_by, pending_decision_proposed_at, team_id
                FROM insights.claim_summary
                WHERE pending_decision_id IS NOT NULL AND market_code = ANY(:markets)
                  AND pending_decision_proposed_by <> :me
                """ + (teamId == null ? "" : " AND team_id = :team") + " ORDER BY pending_decision_proposed_at";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("markets", markets(markets)).param("me", excludeProposer);
        if (teamId != null) {
            spec = spec.param("team", teamId);
        }
        return spec.query((rs, n) -> new ApprovalRow(rs.getObject("claim_id", UUID.class), rs.getString("claim_number"),
                        rs.getString("market_code").trim(), rs.getString("line_of_business"),
                        rs.getObject("pending_decision_id", UUID.class), rs.getString("pending_decision_outcome"),
                        rs.getLong("pending_decision_amount_minor"), rs.getString("currency").trim(),
                        rs.getString("pending_decision_proposed_by"), instant(rs, "pending_decision_proposed_at"),
                        rs.getString("team_id")))
                .list();
    }

    @Override
    public List<WorkloadRow> findWorkload(Set<String> markets, String teamId) {
        Map<String, WorkloadAccumulator> byHandler = new TreeMap<>();
        jdbc.sql("""
                        SELECT handler_id, status, count(*) AS claims, min(submitted_at) AS oldest
                        FROM insights.claim_summary
                        WHERE team_id = :team AND handler_id IS NOT NULL AND market_code = ANY(:markets)
                          AND status IN """ + WORKING + """
                         GROUP BY handler_id, status
                        """)
                .param("team", teamId).param("markets", markets(markets))
                .query(rs -> {
                    WorkloadAccumulator acc = byHandler.computeIfAbsent(rs.getString("handler_id"), h -> new WorkloadAccumulator());
                    acc.add(rs.getString("status"), rs.getInt("claims"), instant(rs, "oldest"));
                });
        List<WorkloadRow> rows = new ArrayList<>();
        byHandler.forEach((handler, acc) -> rows.add(new WorkloadRow(handler, acc.total, acc.byStatus, acc.oldest)));
        rows.sort((a, b) -> Integer.compare(b.openClaims(), a.openClaims()));
        return rows;
    }

    @Override
    public List<PerformanceRow> findPerformance(Set<String> markets, String teamId, Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT handler_id,
                               count(*) AS closed,
                               avg(extract(epoch FROM closed_at - submitted_at)) / 3600.0 AS cycle_hours,
                               100.0 * count(*) FILTER (WHERE decided_at <= sla_due_at)
                                     / NULLIF(count(*) FILTER (WHERE decided_at IS NOT NULL), 0) AS sla_met,
                               100.0 * count(*) FILTER (WHERE reopen_count > 0) / count(*) AS reopen_rate
                        FROM insights.claim_summary
                        WHERE team_id = :team AND handler_id IS NOT NULL AND market_code = ANY(:markets)
                          AND closed_at >= :from AND closed_at < :to
                        GROUP BY handler_id ORDER BY handler_id
                        """)
                .param("team", teamId).param("markets", markets(markets)).param("from", ts(from)).param("to", ts(to))
                .query((rs, n) -> {
                    double slaMet = rs.getDouble("sla_met");
                    Double sla = rs.wasNull() ? null : round(slaMet);
                    return new PerformanceRow(rs.getString("handler_id"), rs.getInt("closed"), round(rs.getDouble("cycle_hours")),
                            sla, round(rs.getDouble("reopen_rate")));
                })
                .list();
    }

    @Override
    public List<ClaimRow> findSlaWatch(Set<String> markets, Instant dueBefore, int limit) {
        return jdbc.sql("SELECT " + ROW_COLUMNS + " FROM insights.claim_summary WHERE market_code = ANY(:markets) AND "
                        + SLA_RUNNING + " AND sla_due_at <= :before ORDER BY sla_due_at, claim_id LIMIT :limit")
                .param("markets", markets(markets)).param("before", ts(dueBefore)).param("limit", limit)
                .query((rs, n) -> row(rs)).list();
    }

    @Override
    public List<ExposureRow> findExposure(Set<String> markets, String lineOfBusiness) {
        String sql = """
                SELECT market_code, line_of_business, currency, count(*) AS claims, sum(reserve_outstanding_minor) AS reserve
                FROM insights.claim_summary
                WHERE market_code = ANY(:markets) AND status IN """ + WORKING
                + (lineOfBusiness == null ? "" : " AND line_of_business = :lob")
                + " GROUP BY market_code, line_of_business, currency ORDER BY market_code, line_of_business, currency";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("markets", markets(markets));
        if (lineOfBusiness != null) {
            spec = spec.param("lob", lineOfBusiness);
        }
        return spec.query((rs, n) -> new ExposureRow(rs.getString("market_code").trim(), rs.getString("line_of_business"),
                rs.getString("currency").trim(), rs.getInt("claims"), rs.getLong("reserve"))).list();
    }

    @Override
    public List<AlertCandidate> findAlertCandidates(Instant dueBefore, String kind, Instant now, int limit) {
        return jdbc.sql("""
                        SELECT s.claim_id, s.claim_number, s.market_code, s.line_of_business, s.handler_id, s.team_id, s.sla_due_at
                        FROM insights.claim_summary s
                        LEFT JOIN insights.sla_alert a
                               ON a.claim_id = s.claim_id AND a.kind = :kind AND a.sla_due_at = s.sla_due_at
                        WHERE a.claim_id IS NULL AND s.sla_due_at <= :before AND
                        """ + SLA_RUNNING.replace("status", "s.status").replace("sla_paused", "s.sla_paused")
                        + " ORDER BY s.sla_due_at LIMIT :limit")
                .param("kind", kind).param("before", ts(dueBefore)).param("limit", limit)
                .query((rs, n) -> new AlertCandidate(rs.getObject("claim_id", UUID.class), rs.getString("claim_number"),
                        rs.getString("market_code").trim(), rs.getString("line_of_business"), rs.getString("handler_id"),
                        rs.getString("team_id"), instant(rs, "sla_due_at")))
                .list();
    }

    private static SqlArrayValue markets(Set<String> markets) {
        return new SqlArrayValue("varchar", markets.toArray());
    }

    private static ClaimRow row(ResultSet rs) throws SQLException {
        return new ClaimRow(rs.getObject("claim_id", UUID.class), rs.getString("claim_number"), rs.getString("market_code").trim(),
                rs.getString("line_of_business"), rs.getString("policy_number"), rs.getString("status"),
                rs.getString("cover_status"), rs.getString("handler_id"), rs.getString("team_id"), instant(rs, "submitted_at"),
                instant(rs, "sla_due_at"), rs.getBoolean("sla_paused"), rs.getBoolean("sla_running"),
                rs.getLong("reserve_outstanding_minor"), rs.getString("currency").trim());
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class WorkloadAccumulator {
        int total;
        Instant oldest;
        final Map<String, Integer> byStatus = new LinkedHashMap<>();

        void add(String status, int count, Instant oldestOfStatus) {
            total += count;
            byStatus.put(status, count);
            if (oldest == null || oldestOfStatus.isBefore(oldest)) {
                oldest = oldestOfStatus;
            }
        }
    }
}
