package com.claims.platform.claims.core.infrastructure;

import com.claims.platform.claims.core.application.ClaimQueries;
import com.claims.platform.claims.core.application.Page;
import com.claims.platform.claims.core.application.PageRequest;
import com.claims.platform.claims.core.domain.Actor;
import com.claims.platform.claims.core.domain.ApprovalRequirement;
import com.claims.platform.claims.core.domain.Assessment;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimFixture;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.ClaimStatus;
import com.claims.platform.claims.core.domain.Decision;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.testsupport.TestPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.claims.platform.claims.core.domain.ClaimFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The aggregate repository against real PostgreSQL 16. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
class JdbcClaimRepositoryIT {
    private static final AtomicLong SEQUENCE = new AtomicLong(1000);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestPostgres.registerFreshDatabase(registry);
        com.claims.platform.testsupport.TestKafka.register(registry, com.claims.platform.claims.ClaimsTopics.ALL);
    }

    @Autowired
    JdbcClaimRepository repository;

    @Autowired
    ClaimQueries queries;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate sql;

    @ParameterizedTest
    @EnumSource(ClaimStatus.class)
    void everyReachableStateSurvivesARoundTrip(ClaimStatus status) {
        Claim original = ClaimFixture.inStatus(status, SEQUENCE.incrementAndGet());

        save(original);
        Claim loaded = repository.findById(original.id()).orElseThrow();

        assertThat(loaded.version()).isEqualTo(1);
        assertThat(loaded).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void assessmentRoundTrips() {
        Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, SEQUENCE.incrementAndGet());
        claim.recordAssessment(UUID.randomUUID(), Assessment.Liability.PARTIAL, sgd("4200"), "50/50", ARUN, at(35));

        save(claim);

        assertThat(repository.findById(claim.id()).orElseThrow().assessment()).isEqualTo(claim.assessment());
    }

    @Test
    void fullLifecycleWritesAuditTrailOutboxAndLedgerConsistently() {
        long seq = SEQUENCE.incrementAndGet();
        Claim claim = ClaimFixture.submitted(seq);
        ClaimId id = claim.id();
        save(claim);
        int events = 1;

        events += step(id, c -> c.confirmCover(Actor.SYSTEM, at(1)));
        events += step(id, c -> c.assign(ARUN.id(), "team-sg-motor", ARUN, at(2)));
        events += step(id, c -> c.startAssessment(ARUN, at(3)));
        events += step(id, c -> c.changeReserve(sgd("5000"), "Initial", ARUN, at(4)));
        events += step(id, c -> c.requestInformation(idFor(REQUEST_ID, seq), List.of("Police report"), at(10_000), ARUN, at(5)));
        events += step(id, c -> c.provideInformation(idFor(REQUEST_ID, seq), "Attached", List.of(), PRIYA, at(6)));
        events += step(id, c -> c.proposeDecision(idFor(DECISION_ID, seq), Decision.Outcome.APPROVE, sgd("4200"), null,
                ApprovalRequirement.NONE, ARUN, at(7)));
        events += step(id, c -> c.instructSettlement(idFor(SETTLEMENT_ID, seq), idFor(INSTRUCTION_ID, seq), PAYEE,
                sgd("4200"), ARUN, at(8)));
        events += step(id, c -> c.confirmPayment(idFor(SETTLEMENT_ID, seq), idFor(INSTRUCTION_ID, seq), "PAY-1", at(9), at(9)));

        Claim settled = repository.findById(id).orElseThrow();
        assertThat(settled.status()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(settled.version()).isEqualTo(10);
        assertThat(settled.outstandingReserve().isZero()).isTrue();

        UUID claimId = id.value();
        assertThat(count("core.claim_event", claimId)).isEqualTo(events);
        assertThat(sql.queryForObject("SELECT count(*) FROM platform.outbox WHERE topic = 'claims.claim-events' AND message_key = ?",
                Integer.class, claimId.toString())).isEqualTo(events);
        assertThat(sql.queryForObject("SELECT count(*) FROM platform.outbox WHERE topic = 'payments.settlement-requests' AND message_key = ?",
                Integer.class, claimId.toString())).isEqualTo(1);
        assertThat(settled.settlement().orElseThrow().payee().accountNumber()).isEqualTo("123456789");
        assertThat(sql.queryForObject("SELECT account_number_enc FROM settlement.settlement WHERE claim_id = ?", String.class, claimId))
                .startsWith("v1:").doesNotContain("123456789");
        assertThat(sql.queryForObject("SELECT sum(amount_minor) FROM handling.reserve_movement WHERE claim_id = ?",
                Long.class, claimId)).isZero();
        assertThat(sql.queryForList("SELECT event_type FROM core.claim_event WHERE claim_id = ? ORDER BY seq", String.class, claimId))
                .startsWith(EventTypes.CLAIM_SUBMITTED, EventTypes.CLAIM_TRIAGED, EventTypes.CLAIM_ASSIGNED)
                .endsWith(EventTypes.PAYMENT_CONFIRMED, EventTypes.CLAIM_SETTLED, EventTypes.RESERVE_CHANGED);
        assertThat(sql.queryForObject("SELECT payload->>'eventId' = id::text AND payload->>'claimId' = message_key "
                        + "FROM platform.outbox WHERE topic = 'claims.claim-events' AND message_key = ? LIMIT 1",
                Boolean.class, claimId.toString())).isTrue();
    }

    @Test
    void concurrentModificationIsDetected() {
        Claim claim = ClaimFixture.inStatus(ClaimStatus.TRIAGED, SEQUENCE.incrementAndGet());
        save(claim);
        Claim first = repository.findById(claim.id()).orElseThrow();
        Claim second = repository.findById(claim.id()).orElseThrow();

        first.assign(ARUN.id(), "t", ARUN, at(10));
        save(first);
        second.assign(KUMAR.id(), "t", KUMAR, at(10));

        assertThatThrownBy(() -> save(second)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.CONCURRENT_UPDATE);
        assertThat(repository.findById(claim.id()).orElseThrow().handlerId()).contains(ARUN.id());
        // fixture events were cleared before the first save; only the winner's ClaimAssigned is stored
        assertThat(count("core.claim_event", claim.id().value())).as("loser's events rolled back").isEqualTo(1);
    }

    @Test
    void ledgerAndAuditTrailAreAppendOnlyInTheDatabase() {
        Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, SEQUENCE.incrementAndGet());
        claim.changeReserve(sgd("6000"), "Revised", ARUN, at(40)); // records an event so both tables have rows
        save(claim);
        assertThat(count("core.claim_event", claim.id().value())).isPositive();

        assertThatThrownBy(() -> sql.update("UPDATE handling.reserve_movement SET amount_minor = 1 WHERE claim_id = ?",
                claim.id().value())).hasMessageContaining("append-only");
        assertThatThrownBy(() -> sql.update("DELETE FROM core.claim_event WHERE claim_id = ?", claim.id().value()))
                .hasMessageContaining("append-only");
    }

    @Test
    void claimantListIsKeysetPaginatedNewestFirstWithActionNeeded() {
        String claimant = "cust-page-" + UUID.randomUUID();
        for (ClaimStatus status : List.of(ClaimStatus.TRIAGED, ClaimStatus.AWAITING_INFO, ClaimStatus.SUBMITTED)) {
            Claim claim = Claim.submit(withClaimant(claimant), Actor.claimant(claimant), at(SEQUENCE.intValue()));
            if (status != ClaimStatus.SUBMITTED) {
                claim.confirmCover(Actor.SYSTEM, at(SEQUENCE.intValue()));
            }
            if (status == ClaimStatus.AWAITING_INFO) {
                claim.assign(ARUN.id(), "t", ARUN, at(SEQUENCE.intValue()));
                claim.startAssessment(ARUN, at(SEQUENCE.intValue()));
                claim.requestInformation(UUID.randomUUID(), List.of("Photo"), at(99_999), ARUN, at(SEQUENCE.intValue()));
            }
            save(claim);
        }

        Page<ClaimQueries.ClaimSummary> first = queries.claimsOfClaimant(claimant, PageRequest.of(null, 2));
        Page<ClaimQueries.ClaimSummary> second = queries.claimsOfClaimant(claimant, PageRequest.of(first.nextCursor(), 2));

        assertThat(first.items()).extracting(ClaimQueries.ClaimSummary::status).containsExactly("SUBMITTED", "AWAITING_INFO");
        assertThat(first.items().get(1).actionNeeded()).isTrue();
        assertThat(first.nextCursor()).isNotNull();
        assertThat(second.items()).extracting(ClaimQueries.ClaimSummary::status).containsExactly("TRIAGED");
        assertThat(second.nextCursor()).isNull();
        assertThat(queries.claimsOfClaimant("nobody", PageRequest.of(null, 5)).items()).isEmpty();
    }

    @Test
    void timelineQueryFiltersToClaimantVisibleEvents() {
        Claim claim = ClaimFixture.submitted(SEQUENCE.incrementAndGet());
        claim.declineCover("POLICY_NOT_IN_FORCE", Actor.SYSTEM, at(1));
        save(claim);

        assertThat(queries.events(claim.id(), false)).extracting(e -> e.type())
                .containsExactly(EventTypes.CLAIM_SUBMITTED, EventTypes.COVER_DECLINED, EventTypes.CLAIM_TRIAGED);
        assertThat(queries.events(claim.id(), true)).extracting(e -> e.type())
                .containsExactly(EventTypes.CLAIM_SUBMITTED, EventTypes.CLAIM_TRIAGED);
    }

    private int step(ClaimId id, Consumer<Claim> command) {
        return tx.execute(status -> {
            Claim claim = repository.findById(id).orElseThrow();
            command.accept(claim);
            int produced = claim.pendingEvents().size();
            repository.save(claim);
            return produced;
        });
    }

    private void save(Claim claim) {
        tx.executeWithoutResult(status -> repository.save(claim));
    }

    private int count(String table, UUID claimId) {
        return sql.queryForObject("SELECT count(*) FROM " + table + " WHERE claim_id = ?", Integer.class, claimId);
    }

    private static com.claims.platform.claims.core.domain.NewClaim withClaimant(String claimant) {
        var base = ClaimFixture.newClaim(com.claims.platform.claims.core.domain.Channel.APP, SEQUENCE.incrementAndGet());
        return new com.claims.platform.claims.core.domain.NewClaim(base.id(), base.number(), base.market(),
                base.lineOfBusiness(), base.policyNumber(), claimant, base.channel(), base.currency(), base.incidentDate(),
                base.claimedAmount(), base.slaTarget());
    }
}
