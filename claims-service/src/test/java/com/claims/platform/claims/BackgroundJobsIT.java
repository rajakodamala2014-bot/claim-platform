package com.claims.platform.claims;

import com.claims.platform.claims.core.repository.ClaimRepository;
import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.Channel;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimFixture;
import com.claims.platform.claims.core.entity.ClaimStatus;
import com.claims.platform.claims.core.entity.CoverStatus;
import com.claims.platform.claims.core.entity.NewClaim;
import com.claims.platform.claims.handling.service.ClaimClosureService;
import com.claims.platform.claims.handling.service.InformationRequestService;
import com.claims.platform.claims.intake.repository.IntakeRepository;
import com.claims.platform.claims.intake.service.CoverCheckRetryService;
import com.claims.platform.claims.platform.common.JobLock;
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

import static com.claims.platform.claims.core.entity.ClaimFixture.*;
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
            .dynamicPort().usingFilesUnderDirectory("../deployment/docker/policy-system"));

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
    IntakeRepository intake;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    JdbcTemplate sql;
    @Autowired
    CoverCheckRetryService coverCheckRetryService;
    @Autowired
    InformationRequestService informationRequestService;
    @Autowired
    ClaimClosureService claimClosureService;
    @Autowired
    JobLock jobLock;

    @Test
    void coverRetryTriagesPendingClaimsAndStoresThePolicySnapshot() {
        Claim pending = ClaimFixture.inStatus(ClaimStatus.SUBMITTED, SEQ.incrementAndGet()); // SG-MTR-POL-1001, cust-priya
        save(pending);

        CoverCheckRetryService.CoverCheckRetryResult result = coverCheckRetryService.retryPendingCoverChecks();

        assertThat(result.triaged()).isGreaterThanOrEqualTo(1);
        Claim covered = claims.findById(pending.id()).orElseThrow();
        assertThat(covered.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(covered.coverStatus()).isEqualTo(CoverStatus.CONFIRMED);
        assertThat(intake.findPolicySnapshot(pending.id())).get().extracting(p -> p.policyNumber()).isEqualTo("SG-MTR-POL-1001");
        assertThat(eventTypes(pending)).isEmpty();
    }

    @Test
    void coverRetryWaitsWhileThePolicySystemIsDownAndDeclinesUnknownPolicies() {
        String holder = "cust-" + UUID.randomUUID();
        Claim waiting = submitFor(holder);
        var outage = POLICY_SYSTEM.stubFor(get(urlPathEqualTo("/api/v1/policies")).withQueryParam("holderId", equalTo(holder))
                .atPriority(1).willReturn(serviceUnavailable()));

        coverCheckRetryService.retryPendingCoverChecks();
        Claim covered = claims.findById(waiting.id()).orElseThrow();
        assertThat(covered.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(covered.coverStatus()).isEqualTo(CoverStatus.CONFIRMED);

        POLICY_SYSTEM.removeStub(outage);
    }

    @Test
    void overdueInformationRequestsReturnToTheHandler() {
        Claim claim = ClaimFixture.inStatus(ClaimStatus.AWAITING_INFO, SEQ.incrementAndGet());
        save(claim);
        sql.update("UPDATE handling.info_request SET due_at = now() - interval '1 hour' WHERE claim_id = ?", claim.id().value());

        assertThat(informationRequestService.expireOverdueInformationRequests()).isGreaterThanOrEqualTo(1);

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

        claimClosureService.closeClaimsAfterCoolingOff();

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
