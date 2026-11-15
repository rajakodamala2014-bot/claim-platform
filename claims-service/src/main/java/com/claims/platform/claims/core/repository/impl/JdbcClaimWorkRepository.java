package com.claims.platform.claims.core.repository.impl;

import com.claims.platform.claims.core.repository.ClaimWorkRepository;
import com.claims.platform.claims.core.entity.ClaimId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.claims.platform.claims.platform.common.JdbcSupport.ts;

@Repository
public class JdbcClaimWorkRepository implements ClaimWorkRepository {
    private final JdbcClient jdbc;

    public JdbcClaimWorkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ClaimId> findPendingCoverChecks(int limit) {
        return jdbc.sql("""
                        SELECT id FROM core.claim WHERE status = 'SUBMITTED' AND cover_status = 'PENDING'
                        ORDER BY submitted_at LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, n) -> new ClaimId(rs.getObject("id", UUID.class))).list();
    }

    @Override
    public List<DueInformationRequest> findDueInformationRequests(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT claim_id, id FROM handling.info_request WHERE status = 'OPEN' AND due_at <= :now
                        ORDER BY due_at LIMIT :limit
                        """)
                .param("now", ts(now)).param("limit", limit)
                .query((rs, n) -> new DueInformationRequest(new ClaimId(rs.getObject("claim_id", UUID.class)),
                        rs.getObject("id", UUID.class)))
                .list();
    }

    @Override
    public List<ClaimId> findClosableClaims(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT c.id FROM core.claim c JOIN platform.market_config m ON m.market_code = c.market_code
                        WHERE c.status IN ('SETTLED', 'REJECTED', 'WITHDRAWN')
                          AND c.status_changed_at + make_interval(days => m.cooling_off_days) <= :now
                        ORDER BY c.status_changed_at LIMIT :limit
                        """)
                .param("now", ts(now)).param("limit", limit)
                .query((rs, n) -> new ClaimId(rs.getObject("id", UUID.class))).list();
    }
}
