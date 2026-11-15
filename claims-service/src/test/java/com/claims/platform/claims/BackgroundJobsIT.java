package com.claims.platform.claims;

import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.domain.Actor;
import com.claims.platform.claims.core.domain.Channel;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimFixture;
import com.claims.platform.claims.core.domain.ClaimStatus;
import com.claims.platform.claims.core.domain.CoverStatus;
import com.claims.platform.claims.core.domain.NewClaim;
import com.claims.platform.claims.handling.application.CloseCoolingOffClaims;
import com.claims.platform.claims.handling.application.ExpireInformationRequests;
import com.claims.platform.claims.intake.application.IntakeRecords;
import com.claims.platform.claims.intake.application.RetryCoverChecks;
import com.claims.platform.claims.platform.infrastructure.JobLock;
import com.claims.platform.testsupport.TestKafka;
import com.claims.platform.testsupport.TestPostgres;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import static com.claims.platform.claims.core.domain.ClaimFixture.*;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

/** The three background jobs and the job lease, against PostgreSQL 16 and the Policy system stub. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "claims.jobs.enabled=false")
@ActiveProfiles("dev")
class BackgroundJobsIT {
    private static final AtomicLong SEQ = new AtomicLong(9000);
    private static final WireMockServer POLICY_SYSTEM = new WireMockServer(WireMockConfiguration.options()
            .dynamicPort().usingFilesUnderDirectory("../docker/policy-system"));

    static {
        POLICY_SYSTEM.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestPostgres.registerFreshDatabase(registry);
        TestKafka.register(registry, ClaimsTopics.ALL);
        registry.add("claims.policy-system.base-url", POLICY_SYSTEM::baseUrl);
    }

    @AfterAll
    static void stop() {
        POLICY_SYSTEM.stop();
    }

    @Autowired
    ClaimRepository claims;
    @Autowired
    IntakeRecords intake;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    JdbcTemplate sql;
    @Autowired
    RetryCoverChecks retryCoverChecks;
    @Autowired
    ExpireInformationRequests expireInformationRequests;
    @Autowired
    CloseCoolingOffClaims closeCoolingOffClaims;
    @Autowired
    JobLock jobLock;

    @Test
    void coverRetryTriagesPendingClaimsAndStoresThePolicySnapshot() {
        Claim pending = ClaimFixture.inStatus(ClaimStatus.SUBMITTED, SEQ.incrementAndGet()); // SG-MTR-POL-1001, cust-priya
        save(pending);

        RetryCoverChecks.Result result = retryCoverChecks.runOnce();

        assertThat(result.triaged()).isGreaterThanOrEqualTo(1);
        Claim triaged = claims.findById(pending.id()).orElseThrow();
        assertThat(triaged.status()).isEqualTo(ClaimStatus.TRIAGED);
        assertThat(triaged.coverStatus()).isEqualTo(CoverStatus.CONFIRMED);
        assertThat(intake.findPolicySnapshot(pending.id())).get().extracting(p -> p.policyNumber()).isEqualTo("SG-MTR-POL-1001");
        assertThat(eventTypes(pending)).containsExactly("ClaimTriaged");
    }

    @Test
    void coverRetryWaitsWhileThePolicySystemIsDownAndDeclinesUnknownPolicies() {
        String holder = "cust-" + UUID.randomUUID();
        Claim waiting = submitFor(holder);
        var outage = POLICY_SYSTEM.stubFor(get(urlPathEqualTo("/api/v1/policies")).withQueryParam("holderId", equalTo(holder))
                .atPriority(1).willReturn(serviceUnavailable()));

        retryCoverChecks.runOnce();
        assertThat(claims.findById(waiting.id()).orElseThrow().status()).isEqualTo(ClaimStatus.SUBMITTED);

        POLICY_SYSTEM.removeStub(outage); // back to the shared stub: this holder has no policies
        retryCoverChecks.runOnce();
        Claim declined = claims.findById(waiting.id()).orElseThrow();
        assertThat(declined.status()).isEqualTo(ClaimStatus.TRIAGED);
        assertThat(declined.coverStatus()).isEqualTo(CoverStatus.DECLINED);
        assertThat(declined.coverDeclineReason()).contains("POLICY_NOT_FOUND");
    }

    @Test
    void overdueInformationRequestsReturnToTheHandler() {
        Claim claim = ClaimFixture.inStatus(ClaimStatus.AWAITING_INFO, SEQ.incrementAndGet());
        save(claim);
        sql.update("UPDATE handling.info_request SET due_at = now() - interval '1 hour' WHERE claim_id = ?", claim.id().value());

        assertThat(expireInformationRequests.runOnce()).isGreaterThanOrEqualTo(1);

        Claim back = claims.findById(claim.id()).orElseThrow();
        assertThat(back.status()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
        assertThat(back.isSlaPaused()).isFalse();
        assertThat(eventTypes(claim)).containsExactly("InformationRequestExpired");
    }

    @Test
    void claimsCloseOnlyAfterTheMarketCoolingOff() {
        Claim old = ClaimFixture.inStatus(ClaimStatus.WITHDRAWN, SEQ.incrementAndGet());
        Claim recent = ClaimFixture.inStatus(ClaimStatus.SETTLED, SEQ.incrementAndGet());
        save(old);
        save(recent);
        sql.update("UPDATE core.claim SET status_changed_at = now() - interval '15 days' WHERE id = ?", old.id().value());
        sql.update("UPDATE core.claim SET status_changed_at = now() - interval '13 days' WHERE id = ?", recent.id().value());

        closeCoolingOffClaims.runOnce();

        assertThat(claims.findById(old.id()).orElseThrow().status()).isEqualTo(ClaimStatus.CLOSED);
        assertThat(claims.findById(recent.id()).orElseThrow().status()).isEqualTo(ClaimStatus.SETTLED);
    }

    @Test
    void aJobRunsOnOneInstanceAtATime() {
        AtomicInteger runs = new AtomicInteger();

        jobLock.runExclusively("it-job", Duration.ofMinutes(5), () -> {
            // a second "instance" trying while the first holds the lease is skipped
            jobLock.runExclusively("it-job", Duration.ofMinutes(5), runs::incrementAndGet);
            return runs.incrementAndGet();
        });
        jobLock.runExclusively("it-job", Duration.ofMinutes(5), runs::incrementAndGet); // released: runs again

        assertThat(runs).hasValue(2);
    }

    private Claim submitFor(String holder) {
        NewClaim base = ClaimFixture.newClaim(Channel.APP, SEQ.incrementAndGet());
        NewClaim mine = new NewClaim(base.id(), base.number(), base.market(), base.lineOfBusiness(), base.policyNumber(),
                holder, base.channel(), base.currency(), base.incidentDate(), null, base.slaTarget());
        Claim claim = Claim.submit(mine, Actor.claimant(holder), Instant.now());
        claim.pullPendingEvents();
        save(claim);
        return claim;
    }

    private void save(Claim claim) {
        tx.executeWithoutResult(s -> claims.save(claim));
    }

    private List<String> eventTypes(Claim claim) {
        return sql.queryForList("SELECT event_type FROM core.claim_event WHERE claim_id = ? ORDER BY seq", String.class,
                claim.id().value());
    }
}
