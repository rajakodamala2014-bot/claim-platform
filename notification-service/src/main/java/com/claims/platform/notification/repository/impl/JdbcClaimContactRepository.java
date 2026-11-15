package com.claims.platform.notification.repository.impl;

import com.claims.platform.notification.entity.ClaimContact;
import com.claims.platform.notification.repository.ClaimContactRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * The claim contact projection (who the customer, handler and team are), upserted from claims events inside the
 * consumer transaction. Stays on JdbcClient: it is an INSERT ... ON CONFLICT upsert, one statement and no read first.
 * Preferences, templates and the staff directory are JPA (the {@code Jpa*Repository} classes).
 */
@Repository
public class JdbcClaimContactRepository implements ClaimContactRepository {
    private final JdbcClient jdbc;

    public JdbcClaimContactRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ claim contacts

    @Override
    public Optional<ClaimContact> findByClaimId(UUID claimId) {
        return jdbc.sql("SELECT * FROM notification.claim_contact WHERE claim_id = :id").param("id", claimId)
                .query((rs, n) -> new ClaimContact(rs.getObject("claim_id", UUID.class), rs.getString("claim_number"),
                        rs.getString("claimant_id"), rs.getString("market_code").trim(), rs.getString("line_of_business"),
                        rs.getString("handler_id"), rs.getString("team_id")))
                .optional();
    }

    @Override
    public void save(ClaimContact c) {
        jdbc.sql("""
                        INSERT INTO notification.claim_contact (claim_id, claim_number, claimant_id, market_code, line_of_business,
                            handler_id, team_id)
                        VALUES (:id, :number, :claimant, :market, :lob, :handler, :team)
                        ON CONFLICT (claim_id) DO UPDATE SET handler_id = EXCLUDED.handler_id, team_id = EXCLUDED.team_id,
                            updated_at = now()
                        """)
                .param("id", c.claimId()).param("number", c.claimNumber()).param("claimant", c.claimantId())
                .param("market", c.market()).param("lob", c.lineOfBusiness()).param("handler", c.handlerId())
                .param("team", c.teamId())
                .update();
    }
}
