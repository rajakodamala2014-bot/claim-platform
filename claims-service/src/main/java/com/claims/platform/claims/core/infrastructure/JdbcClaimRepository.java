package com.claims.platform.claims.core.infrastructure;

import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.domain.ApprovalRequirement;
import com.claims.platform.claims.core.domain.Assessment;
import com.claims.platform.claims.core.domain.Channel;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimEvent;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.ClaimNumber;
import com.claims.platform.claims.core.domain.ClaimState;
import com.claims.platform.claims.core.domain.ClaimStatus;
import com.claims.platform.claims.core.domain.CoverStatus;
import com.claims.platform.claims.core.domain.Decision;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.claims.core.domain.InformationRequest;
import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.core.domain.Payee;
import com.claims.platform.claims.core.domain.ReserveMovement;
import com.claims.platform.claims.core.domain.Settlement;
import com.claims.platform.claims.platform.infrastructure.FieldCipher;
import com.claims.platform.claims.platform.infrastructure.Jdbc;
import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.common.messaging.Topics;
import com.claims.platform.common.money.Money;
import com.claims.platform.common.payments.SettlementRequest;
import com.claims.platform.web.correlation.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.claims.platform.claims.platform.infrastructure.Jdbc.instant;
import static com.claims.platform.claims.platform.infrastructure.Jdbc.nullableLong;
import static com.claims.platform.claims.platform.infrastructure.Jdbc.trimmed;
import static com.claims.platform.claims.platform.infrastructure.Jdbc.ts;

/**
 * Maps the framework-free Claim aggregate to the claims tables with plain SQL. One {@link #save} writes the
 * claim row (optimistic version check), its children, its new events to core.claim_event and the outbox —
 * all in the caller's transaction, so state, audit trail and published events can never disagree.
 */
@Repository
public class JdbcClaimRepository implements ClaimRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final OutboxWriter outbox;
    private final FieldCipher cipher;

    public JdbcClaimRepository(JdbcClient jdbc, ObjectMapper json, OutboxWriter outbox, FieldCipher cipher) {
        this.cipher = cipher;
        this.jdbc = jdbc;
        this.json = json;
        this.outbox = outbox;
    }

    // ------------------------------------------------------------------ load

    @Override
    public Optional<Claim> findById(ClaimId id) {
        return jdbc.sql("SELECT * FROM core.claim WHERE id = :id").param("id", id.value())
                .query((rs, n) -> rs.getString("currency").trim())
                .optional()
                .map(currency -> load(id, currency));
    }

    private Claim load(ClaimId id, String currency) {
        UUID claimId = id.value();
        List<ReserveMovement> movements = jdbc.sql("SELECT * FROM handling.reserve_movement WHERE claim_id = :id ORDER BY seq")
                .param("id", claimId).query((rs, n) -> movement(rs)).list();
        List<InformationRequest> requests = jdbc.sql(
                        "SELECT * FROM handling.info_request WHERE claim_id = :id ORDER BY requested_at, id")
                .param("id", claimId).query((rs, n) -> infoRequest(rs)).list();
        Assessment assessment = jdbc.sql(
                        "SELECT * FROM handling.assessment WHERE claim_id = :id ORDER BY assessed_at DESC LIMIT 1")
                .param("id", claimId).query((rs, n) -> assessment(rs)).optional().orElse(null);
        Decision decision = jdbc.sql(
                        "SELECT * FROM handling.decision WHERE claim_id = :id ORDER BY proposed_at DESC, created_at DESC LIMIT 1")
                .param("id", claimId).query((rs, n) -> decision(rs)).optional().orElse(null);
        Settlement settlement = jdbc.sql("SELECT * FROM settlement.settlement WHERE claim_id = :id")
                .param("id", claimId).query((rs, n) -> settlement(rs)).optional().orElse(null);
        return jdbc.sql("SELECT * FROM core.claim WHERE id = :id").param("id", claimId)
                .query((rs, n) -> Claim.restore(state(rs, currency, movements, requests, assessment, decision, settlement)))
                .single();
    }

    private ClaimState state(ResultSet rs, String currency, List<ReserveMovement> movements, List<InformationRequest> requests,
                             Assessment assessment, Decision decision, Settlement settlement) throws SQLException {
        Long claimed = nullableLong(rs, "claimed_amount_minor");
        Long approved = nullableLong(rs, "approved_amount_minor");
        return new ClaimState(
                new ClaimId(rs.getObject("id", UUID.class)),
                new ClaimNumber(rs.getString("claim_number")),
                MarketCode.of(trimmed(rs, "market_code")),
                LineOfBusiness.valueOf(rs.getString("line_of_business")),
                rs.getString("policy_number"),
                rs.getString("claimant_id"),
                Channel.valueOf(rs.getString("channel")),
                currency,
                instant(rs, "incident_at"),
                claimed == null ? null : Money.ofMinor(claimed, currency),
                instant(rs, "submitted_at"),
                ClaimStatus.valueOf(rs.getString("status")),
                instant(rs, "status_changed_at"),
                CoverStatus.valueOf(rs.getString("cover_status")),
                rs.getString("cover_decline_reason"),
                rs.getString("handler_id"),
                rs.getString("team_id"),
                instant(rs, "sla_due_at"),
                instant(rs, "sla_paused_at"),
                approved == null ? null : Money.ofMinor(approved, currency),
                movements, requests, assessment, decision, settlement,
                rs.getLong("version"));
    }

    private ReserveMovement movement(ResultSet rs) throws SQLException {
        String currency = trimmed(rs, "currency");
        return new ReserveMovement(rs.getObject("id", UUID.class),
                ReserveMovement.Type.valueOf(rs.getString("movement_type")),
                Money.ofMinor(rs.getLong("amount_minor"), currency),
                Money.ofMinor(rs.getLong("balance_after_minor"), currency),
                rs.getString("reason"), rs.getString("actor_id"), instant(rs, "occurred_at"));
    }

    private InformationRequest infoRequest(ResultSet rs) throws SQLException {
        return new InformationRequest(rs.getObject("id", UUID.class), Jdbc.strings(rs, "items"), instant(rs, "due_at"),
                rs.getString("requested_by"), instant(rs, "requested_at"),
                InformationRequest.Status.valueOf(rs.getString("status")), rs.getString("answer"),
                Jdbc.uuids(rs, "document_ids"), instant(rs, "closed_at"));
    }

    private Assessment assessment(ResultSet rs) throws SQLException {
        return new Assessment(rs.getObject("id", UUID.class), Assessment.Liability.valueOf(rs.getString("liability")),
                Money.ofMinor(rs.getLong("assessed_amount_minor"), trimmed(rs, "currency")),
                rs.getString("notes"), rs.getString("assessed_by"), instant(rs, "assessed_at"));
    }

    private Decision decision(ResultSet rs) throws SQLException {
        return new Decision(rs.getObject("id", UUID.class), Decision.Outcome.valueOf(rs.getString("outcome")),
                Money.ofMinor(rs.getLong("amount_minor"), trimmed(rs, "currency")), rs.getString("reason_code"),
                rs.getString("proposed_by"), instant(rs, "proposed_at"),
                ApprovalRequirement.valueOf(rs.getString("approval")), Decision.Status.valueOf(rs.getString("status")),
                rs.getString("decided_by"), instant(rs, "decided_at"));
    }

    private Settlement settlement(ResultSet rs) throws SQLException {
        return new Settlement(rs.getObject("id", UUID.class),
                Money.ofMinor(rs.getLong("amount_minor"), trimmed(rs, "currency")),
                new Payee(rs.getString("payee_name"), rs.getString("bank_code"),
                        cipher.decrypt(rs.getString("account_number_enc"), rs.getObject("id", UUID.class).toString())),
                rs.getObject("instruction_id", UUID.class), rs.getInt("attempts"),
                Settlement.Status.valueOf(rs.getString("status")), rs.getString("payment_ref"),
                rs.getString("failure_reason"), instant(rs, "instructed_at"), instant(rs, "completed_at"));
    }

    // ------------------------------------------------------------------ save

    @Override
    public void save(Claim claim) {
        long newVersion = claim.version() + 1;
        if (claim.version() == 0) {
            insertClaim(claim, newVersion);
        } else {
            updateClaim(claim, newVersion);
        }
        UUID id = claim.id().value();
        String market = claim.market().value();
        claim.reserveMovements().forEach(m -> insertMovement(id, market, claim.lineOfBusiness(), m));
        claim.informationRequests().forEach(r -> upsertInfoRequest(id, market, r));
        claim.assessment().ifPresent(a -> upsertAssessment(id, market, a));
        claim.decision().ifPresent(d -> upsertDecision(id, market, d));
        claim.settlement().ifPresent(s -> upsertSettlement(id, market, s));
        for (ClaimEvent event : claim.pendingEvents()) {
            appendEvent(claim, event);
        }
        claim.pullPendingEvents();
        claim.markSaved(newVersion);
    }

    @Override
    public long nextClaimSequence() {
        return jdbc.sql("SELECT nextval('core.claim_number_seq')").query(Long.class).single();
    }

    private void insertClaim(Claim c, long version) {
        jdbc.sql("""
                        INSERT INTO core.claim (id, claim_number, market_code, line_of_business, policy_number, claimant_id,
                            channel, currency, incident_at, claimed_amount_minor, status, status_changed_at, cover_status,
                            cover_decline_reason, handler_id, team_id, sla_due_at, sla_paused_at, approved_amount_minor,
                            submitted_at, version)
                        VALUES (:id, :number, :market, :lob, :policy, :claimant, :channel, :currency, :incidentAt, :claimed,
                            :status, :statusChangedAt, :cover, :coverReason, :handler, :team, :slaDue, :slaPaused, :approved,
                            :submittedAt, :version)
                        """)
                .param("id", c.id().value())
                .param("number", c.number().value())
                .param("market", c.market().value())
                .param("lob", c.lineOfBusiness().name())
                .param("policy", c.policyNumber())
                .param("claimant", c.claimantId())
                .param("channel", c.channel().name())
                .param("currency", c.currency())
                .param("incidentAt", ts(c.incidentDate()))
                .param("claimed", c.claimedAmount().map(Money::amountMinor).orElse(null))
                .param("submittedAt", ts(c.submittedAt()))
                .params(mutableColumns(c, version))
                .update();
    }

    private void updateClaim(Claim c, long version) {
        int updated = jdbc.sql("""
                        UPDATE core.claim SET status = :status, status_changed_at = :statusChangedAt, cover_status = :cover,
                            cover_decline_reason = :coverReason, handler_id = :handler, team_id = :team, sla_due_at = :slaDue,
                            sla_paused_at = :slaPaused, approved_amount_minor = :approved, version = :version, updated_at = now()
                        WHERE id = :id AND version = :expectedVersion
                        """)
                .param("id", c.id().value())
                .param("expectedVersion", c.version())
                .params(mutableColumns(c, version))
                .update();
        if (updated == 0) {
            throw new DomainException(ErrorCode.CONCURRENT_UPDATE,
                    "Claim " + c.number() + " was changed by someone else; reload and try again");
        }
    }

    private static java.util.Map<String, Object> mutableColumns(Claim c, long version) {
        java.util.Map<String, Object> p = new java.util.HashMap<>();
        p.put("status", c.status().name());
        p.put("statusChangedAt", ts(c.statusChangedAt()));
        p.put("cover", c.coverStatus().name());
        p.put("coverReason", c.coverDeclineReason().orElse(null));
        p.put("handler", c.handlerId().orElse(null));
        p.put("team", c.teamId().orElse(null));
        p.put("slaDue", ts(c.slaDueAt()));
        p.put("slaPaused", ts(c.slaPausedAt().orElse(null)));
        p.put("approved", c.approvedAmount().map(Money::amountMinor).orElse(null));
        p.put("version", version);
        return p;
    }

    /** Ledger rows are immutable: existing ids are skipped, new ones appended. */
    private void insertMovement(UUID claimId, String market, LineOfBusiness lob, ReserveMovement m) {
        jdbc.sql("""
                        INSERT INTO handling.reserve_movement (id, claim_id, market_code, line_of_business, movement_type,
                            amount_minor, balance_after_minor, currency, reason, actor_id, occurred_at)
                        VALUES (:id, :claim, :market, :lob, :type, :amount, :balance, :currency, :reason, :actor, :at)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", m.id()).param("claim", claimId).param("market", market).param("lob", lob.name())
                .param("type", m.type().name()).param("amount", m.amount().amountMinor())
                .param("balance", m.balanceAfter().amountMinor()).param("currency", m.amount().currency())
                .param("reason", m.reason()).param("actor", m.actorId()).param("at", ts(m.at()))
                .update();
    }

    private void upsertInfoRequest(UUID claimId, String market, InformationRequest r) {
        jdbc.sql("""
                        INSERT INTO handling.info_request (id, claim_id, market_code, items, due_at, requested_by, requested_at,
                            status, answer, document_ids, closed_at)
                        VALUES (:id, :claim, :market, :items, :due, :by, :at, :status, :answer, :docs, :closed)
                        ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, answer = EXCLUDED.answer,
                            document_ids = EXCLUDED.document_ids, closed_at = EXCLUDED.closed_at, updated_at = now()
                        """)
                .param("id", r.id()).param("claim", claimId).param("market", market)
                .param("items", new SqlArrayValue("text", r.items().toArray()))
                .param("due", ts(r.dueAt())).param("by", r.requestedBy()).param("at", ts(r.requestedAt()))
                .param("status", r.status().name()).param("answer", r.answer())
                .param("docs", new SqlArrayValue("uuid", r.documentIds().toArray()))
                .param("closed", ts(r.closedAt()))
                .update();
    }

    private void upsertAssessment(UUID claimId, String market, Assessment a) {
        jdbc.sql("""
                        INSERT INTO handling.assessment (id, claim_id, market_code, liability, assessed_amount_minor, currency,
                            notes, assessed_by, assessed_at)
                        VALUES (:id, :claim, :market, :liability, :amount, :currency, :notes, :by, :at)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", a.id()).param("claim", claimId).param("market", market)
                .param("liability", a.liability().name()).param("amount", a.assessedAmount().amountMinor())
                .param("currency", a.assessedAmount().currency()).param("notes", a.notes())
                .param("by", a.assessedBy()).param("at", ts(a.assessedAt()))
                .update();
    }

    private void upsertDecision(UUID claimId, String market, Decision d) {
        jdbc.sql("""
                        INSERT INTO handling.decision (id, claim_id, market_code, outcome, amount_minor, currency, reason_code,
                            proposed_by, proposed_at, approval, status, decided_by, decided_at)
                        VALUES (:id, :claim, :market, :outcome, :amount, :currency, :reason, :by, :at, :approval, :status,
                            :decidedBy, :decidedAt)
                        ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, decided_by = EXCLUDED.decided_by,
                            decided_at = EXCLUDED.decided_at, updated_at = now()
                        """)
                .param("id", d.id()).param("claim", claimId).param("market", market)
                .param("outcome", d.outcome().name()).param("amount", d.amount().amountMinor())
                .param("currency", d.amount().currency()).param("reason", d.reasonCode())
                .param("by", d.proposedBy()).param("at", ts(d.proposedAt())).param("approval", d.approval().name())
                .param("status", d.status().name()).param("decidedBy", d.decidedBy()).param("decidedAt", ts(d.decidedAt()))
                .update();
    }

    private void upsertSettlement(UUID claimId, String market, Settlement s) {
        jdbc.sql("""
                        INSERT INTO settlement.settlement (id, claim_id, market_code, amount_minor, currency, payee_name,
                            bank_code, account_number_enc, instruction_id, attempts, status, payment_ref, failure_reason,
                            instructed_at, completed_at)
                        VALUES (:id, :claim, :market, :amount, :currency, :payee, :bank, :account, :instruction, :attempts,
                            :status, :ref, :failure, :instructedAt, :completedAt)
                        ON CONFLICT (id) DO UPDATE SET payee_name = EXCLUDED.payee_name, bank_code = EXCLUDED.bank_code,
                            account_number_enc = EXCLUDED.account_number_enc, instruction_id = EXCLUDED.instruction_id,
                            attempts = EXCLUDED.attempts, status = EXCLUDED.status, payment_ref = EXCLUDED.payment_ref,
                            failure_reason = EXCLUDED.failure_reason, instructed_at = EXCLUDED.instructed_at,
                            completed_at = EXCLUDED.completed_at, updated_at = now()
                        """)
                .param("id", s.id()).param("claim", claimId).param("market", market)
                .param("amount", s.amount().amountMinor()).param("currency", s.amount().currency())
                .param("payee", s.payee().name()).param("bank", s.payee().bankCode())
                .param("account", cipher.encrypt(s.payee().accountNumber(), s.id().toString())).param("instruction", s.instructionId())
                .param("attempts", s.attempts()).param("status", s.status().name()).param("ref", s.paymentRef())
                .param("failure", s.failureReason()).param("instructedAt", ts(s.instructedAt()))
                .param("completedAt", ts(s.completedAt()))
                .update();
    }

    private void appendEvent(Claim claim, ClaimEvent event) {
        jdbc.sql("""
                        INSERT INTO core.claim_event (id, claim_id, market_code, event_type, visibility, actor_id, actor_role,
                            occurred_at, data)
                        VALUES (:id, :claim, :market, :type, :visibility, :actor, :role, :at, :data::jsonb)
                        """)
                .param("id", event.eventId()).param("claim", claim.id().value()).param("market", claim.market().value())
                .param("type", event.type()).param("visibility", event.visibility().name())
                .param("actor", event.actorId()).param("role", event.actorRole().name())
                .param("at", ts(event.occurredAt())).param("data", Jdbc.json(json, event.data()))
                .update();

        String key = claim.id().toString();
        EventEnvelope envelope = new EventEnvelope(event.eventId(), event.type(), 1, event.occurredAt(), claim.id().value(),
                claim.market().value(), claim.lineOfBusiness().name(), CorrelationIdFilter.current().orElse(null),
                withActor(event));
        outbox.append(event.eventId(), Topics.CLAIM_EVENTS, key, event.type(), envelope);

        if (EventTypes.SETTLEMENT_INSTRUCTED.equals(event.type())) {
            Settlement s = claim.settlement().orElseThrow();
            SettlementRequest request = new SettlementRequest(s.instructionId(), s.id(), claim.id().value(),
                    claim.number().value(), s.payee().name(), s.payee().bankCode(), s.payee().accountNumber(),
                    s.amount().amountMinor(), s.amount().currency(), event.occurredAt());
            outbox.append(s.instructionId(), Topics.SETTLEMENT_REQUESTS, key, "SettlementRequest", request);
        }
    }

    /** Consumers (notifications, insights) need to know who acted and the claim reference. */
    private static java.util.Map<String, Object> withActor(ClaimEvent event) {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>(event.data());
        data.put("actorId", event.actorId());
        data.put("actorRole", event.actorRole().name());
        data.put("visibility", event.visibility().name());
        return data;
    }
}
