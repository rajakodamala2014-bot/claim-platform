package com.claims.platform.insights.infrastructure;

import com.claims.platform.insights.application.ClaimSummaryStore;
import com.claims.platform.insights.domain.ClaimSummary;
import com.claims.platform.insights.domain.ClaimSummary.PendingDecision;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcClaimSummaryStore implements ClaimSummaryStore {
    private final JdbcClient jdbc;

    public JdbcClaimSummaryStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ClaimSummary> find(UUID claimId) {
        return jdbc.sql("SELECT * FROM insights.claim_summary WHERE claim_id = :id").param("id", claimId)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public void save(ClaimSummary s) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", s.claimId());
        p.put("number", s.claimNumber());
        p.put("market", s.market());
        p.put("lob", s.lineOfBusiness());
        p.put("policy", s.policyNumber());
        p.put("claimant", s.claimantId());
        p.put("channel", s.channel());
        p.put("status", s.status());
        p.put("cover", s.coverStatus());
        p.put("handler", s.handlerId());
        p.put("team", s.teamId());
        p.put("submitted", ts(s.submittedAt()));
        p.put("due", ts(s.slaDueAt()));
        p.put("paused", s.slaPaused());
        p.put("decided", ts(s.decidedAt()));
        p.put("settled", ts(s.settledAt()));
        p.put("closed", ts(s.closedAt()));
        p.put("reserve", s.reserveOutstandingMinor());
        p.put("currency", s.currency());
        PendingDecision d = s.pendingDecision().orElse(null);
        p.put("decisionId", d == null ? null : d.decisionId());
        p.put("decisionOutcome", d == null ? null : d.outcome());
        p.put("decisionAmount", d == null ? null : d.amountMinor());
        p.put("decisionBy", d == null ? null : d.proposedBy());
        p.put("decisionAt", d == null ? null : ts(d.proposedAt()));
        p.put("reopens", s.reopenCount());
        p.put("lastEvent", ts(s.lastEventAt()));
        jdbc.sql("""
                        INSERT INTO insights.claim_summary (claim_id, claim_number, market_code, line_of_business, policy_number,
                            claimant_id, channel, status, cover_status, handler_id, team_id, submitted_at, sla_due_at, sla_paused,
                            decided_at, settled_at, closed_at, reserve_outstanding_minor, currency, pending_decision_id,
                            pending_decision_outcome, pending_decision_amount_minor, pending_decision_proposed_by,
                            pending_decision_proposed_at, reopen_count, last_event_at)
                        VALUES (:id, :number, :market, :lob, :policy, :claimant, :channel, :status, :cover, :handler, :team,
                            :submitted, :due, :paused, :decided, :settled, :closed, :reserve, :currency, :decisionId,
                            :decisionOutcome, :decisionAmount, :decisionBy, :decisionAt, :reopens, :lastEvent)
                        ON CONFLICT (claim_id) DO UPDATE SET status = EXCLUDED.status, cover_status = EXCLUDED.cover_status,
                            handler_id = EXCLUDED.handler_id, team_id = EXCLUDED.team_id, sla_due_at = EXCLUDED.sla_due_at,
                            sla_paused = EXCLUDED.sla_paused, decided_at = EXCLUDED.decided_at, settled_at = EXCLUDED.settled_at,
                            closed_at = EXCLUDED.closed_at, reserve_outstanding_minor = EXCLUDED.reserve_outstanding_minor,
                            currency = EXCLUDED.currency, pending_decision_id = EXCLUDED.pending_decision_id,
                            pending_decision_outcome = EXCLUDED.pending_decision_outcome,
                            pending_decision_amount_minor = EXCLUDED.pending_decision_amount_minor,
                            pending_decision_proposed_by = EXCLUDED.pending_decision_proposed_by,
                            pending_decision_proposed_at = EXCLUDED.pending_decision_proposed_at,
                            reopen_count = EXCLUDED.reopen_count, last_event_at = EXCLUDED.last_event_at, updated_at = now()
                        """)
                .params(p)
                .update();
    }

    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static ClaimSummary map(ResultSet rs) throws SQLException {
        UUID decisionId = rs.getObject("pending_decision_id", UUID.class);
        PendingDecision pending = decisionId == null ? null : new PendingDecision(decisionId,
                rs.getString("pending_decision_outcome"), rs.getLong("pending_decision_amount_minor"),
                rs.getString("pending_decision_proposed_by"), instant(rs, "pending_decision_proposed_at"));
        return new ClaimSummary(rs.getObject("claim_id", UUID.class), rs.getString("claim_number"),
                rs.getString("market_code").trim(), rs.getString("line_of_business"), rs.getString("policy_number"),
                rs.getString("claimant_id"), rs.getString("channel"), instant(rs, "submitted_at"), rs.getString("status"),
                rs.getString("cover_status"), rs.getString("handler_id"), rs.getString("team_id"), instant(rs, "sla_due_at"),
                rs.getBoolean("sla_paused"), instant(rs, "decided_at"), instant(rs, "settled_at"), instant(rs, "closed_at"),
                rs.getLong("reserve_outstanding_minor"), rs.getString("currency").trim(), pending, rs.getInt("reopen_count"),
                instant(rs, "last_event_at"));
    }
}
