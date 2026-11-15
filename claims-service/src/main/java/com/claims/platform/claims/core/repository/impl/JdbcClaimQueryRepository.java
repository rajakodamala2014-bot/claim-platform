package com.claims.platform.claims.core.repository.impl;

import com.claims.platform.claims.core.repository.ClaimQueryRepository;
import com.claims.platform.claims.core.common.Page;
import com.claims.platform.claims.core.common.PageRequest;
import com.claims.platform.claims.core.entity.ClaimEvent;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.Role;
import com.claims.platform.claims.platform.common.JdbcSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static com.claims.platform.claims.platform.common.JdbcSupport.instant;
import static com.claims.platform.claims.platform.common.JdbcSupport.ts;

@Repository
public class JdbcClaimQueryRepository implements ClaimQueryRepository {
    private static final String SUMMARY = """
            SELECT c.id, c.claim_number, c.status, c.line_of_business, c.policy_number, c.submitted_at, c.status_changed_at,
                   EXISTS (SELECT 1 FROM handling.info_request r WHERE r.claim_id = c.id AND r.status = 'OPEN') AS action_needed
            FROM core.claim c
            WHERE c.claimant_id = :claimant
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcClaimQueryRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Page<ClaimSummary> findClaimsOfClaimant(String claimantId, PageRequest page) {
        String sql = SUMMARY
                + (page.isFirstPage() ? "" : " AND (c.submitted_at, c.id) < (:afterTs, :afterId)")
                + " ORDER BY c.submitted_at DESC, c.id DESC LIMIT :limit";
        JdbcClient.StatementSpec statement = jdbc.sql(sql)
                .param("claimant", claimantId)
                .param("limit", page.limit() + 1);
        if (!page.isFirstPage()) {
            statement = statement.param("afterTs", ts(page.afterTimestamp())).param("afterId", page.afterId());
        }
        List<ClaimSummary> rows = statement.query((rs, n) -> new ClaimSummary(
                rs.getObject("id", UUID.class), rs.getString("claim_number"), rs.getString("status"),
                rs.getString("line_of_business"), rs.getString("policy_number"), instant(rs, "submitted_at"),
                instant(rs, "status_changed_at"), rs.getBoolean("action_needed"))).list();
        if (rows.size() <= page.limit()) {
            return new Page<>(rows, null);
        }
        List<ClaimSummary> items = rows.subList(0, page.limit());
        ClaimSummary last = items.getLast();
        return new Page<>(items, PageRequest.cursorOf(last.submittedAt(), last.claimId()));
    }

    @Override
    public List<ClaimEvent> events(ClaimId claimId, boolean claimantVisibleOnly) {
        String sql = "SELECT * FROM core.claim_event WHERE claim_id = :claim"
                + (claimantVisibleOnly ? " AND visibility = 'CLAIMANT'" : "") + " ORDER BY seq";
        return jdbc.sql(sql).param("claim", claimId.value())
                .query((rs, n) -> new ClaimEvent(rs.getObject("id", UUID.class), rs.getString("event_type"),
                        ClaimEvent.Visibility.valueOf(rs.getString("visibility")), rs.getString("actor_id"),
                        Role.valueOf(rs.getString("actor_role")), instant(rs, "occurred_at"),
                        JdbcSupport.jsonMap(json, rs.getString("data"))))
                .list();
    }
}
