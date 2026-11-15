package com.claims.platform.claims.handling.service.impl;

import com.claims.platform.claims.handling.repository.AuthorityLimitRepository;
import com.claims.platform.claims.handling.entity.ReviewAction;
import com.claims.platform.claims.handling.service.ClaimHandlingService;
import com.claims.platform.claims.core.repository.InMemoryClaimRepository;
import com.claims.platform.claims.core.service.StaffClaimCommandService;
import com.claims.platform.claims.core.service.impl.StaffClaimCommandServiceImpl;
import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimFixture;
import com.claims.platform.claims.core.entity.ClaimStatus;
import com.claims.platform.claims.core.entity.Decision;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.core.entity.Role;
import com.claims.platform.claims.handling.entity.AuthorityLimit;
import com.claims.platform.claims.handling.entity.AuthorityPolicy;
import com.claims.platform.common.event.EventTypes;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static com.claims.platform.claims.core.entity.ClaimFixture.sgd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Staff use cases over an in-memory repository: scoping, versioning and authority routing. */
class StaffUseCasesTest {
    private static final MarketCode SG = MarketCode.of("SG");
    private static final StaffContext ARUN = new StaffContext(ClaimFixture.ARUN, Set.of(SG), "team-sg-motor");
    private static final StaffContext KUMAR = new StaffContext(ClaimFixture.KUMAR, Set.of(SG), "team-sg-motor");
    private static final StaffContext MEERA = new StaffContext(ClaimFixture.MEERA, Set.of(SG, MarketCode.of("MY")), "team-sg-motor");
    private static final StaffContext LEE = new StaffContext(ClaimFixture.LEE, Set.of(SG), null);
    private static final StaffContext MY_HANDLER = new StaffContext(Actor.handler("h-my"), Set.of(MarketCode.of("MY")), null);

    private final InMemoryClaimRepository repository = new InMemoryClaimRepository();
    private final StaffClaimCommandService staffCommands = new StaffClaimCommandServiceImpl(repository, InMemoryClaimRepository.directUnitOfWork());
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC);
    private final AuthorityLimitRepository limits = () -> new AuthorityPolicy(List.of(
            new AuthorityLimit(Role.HANDLER, SG, LineOfBusiness.MOTOR, sgd("10000")),
            new AuthorityLimit(Role.TEAM_LEAD, SG, LineOfBusiness.MOTOR, sgd("50000")),
            new AuthorityLimit(Role.MANAGER, SG, LineOfBusiness.MOTOR, sgd("500000"))));
    /** No test here requests information, the only command that reads the market configuration. */
    private final ClaimHandlingService claimHandlingService = new ClaimHandlingServiceImpl(staffCommands, clock, null, limits);

    @Test
    void pickUpDefaultsToTheCallerAndTheirTeamAndBumpsTheVersion() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.TRIAGED));

        var outcome = claimHandlingService.assignClaim(claim.id(), ARUN, 1, null, null);

        assertThat(outcome.value()).isEqualTo("h-arun");
        assertThat(outcome.status()).isEqualTo("ASSIGNED");
        assertThat(outcome.version()).isEqualTo(2);
        assertThat(claim.teamId()).contains("team-sg-motor");
    }

    @Test
    void secondPickUpIsAlreadyAssignedAndStaleVersionsAreRejected() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.TRIAGED));
        claimHandlingService.assignClaim(claim.id(), ARUN, 1, null, null);

        expectCode(() -> claimHandlingService.assignClaim(claim.id(), KUMAR, 2, null, null), ErrorCode.CLAIM_ALREADY_ASSIGNED);
        expectCode(() -> claimHandlingService.assignClaim(claim.id(), MEERA, 1, "h-kumar", null), ErrorCode.STALE_VERSION);
        assertThat(claim.handlerId()).contains("h-arun");
    }

    @Test
    void foreignMarketIsForbiddenBeforeTheVersionIsChecked() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.TRIAGED));

        expectCode(() -> claimHandlingService.assignClaim(claim.id(), MY_HANDLER, 999, null, null), ErrorCode.FORBIDDEN_MARKET);
        expectCode(() -> staffCommands.loadVisibleClaim(claim.id(), MY_HANDLER), ErrorCode.FORBIDDEN_MARKET);
        expectCode(() -> staffCommands.loadVisibleClaim(com.claims.platform.claims.core.entity.ClaimId.newId(), ARUN),
                ErrorCode.CLAIM_NOT_FOUND);
    }

    @Test
    void withinAuthorityIsFinalAboveGoesToATeamLead() {
        Claim small = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, 501));
        Claim large = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, 502));

        var final1 = claimHandlingService.proposeDecision(small.id(), ARUN, 1, Decision.Outcome.APPROVE, sgd("10000"), null);
        var pending = claimHandlingService.proposeDecision(large.id(), ARUN, 1, Decision.Outcome.APPROVE, sgd("10000.01"), null);

        assertThat(final1.status()).isEqualTo("APPROVED");
        assertThat(final1.value().status()).isEqualTo(Decision.Status.FINAL);
        assertThat(pending.status()).isEqualTo("DECISION_PENDING");
        assertThat(pending.value().status()).isEqualTo(Decision.Status.AWAITING_APPROVAL);
    }

    @Test
    void rejectionAuthorityUsesTheOutstandingReserve() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT)); // reserve 5,000
        claimHandlingService.setReserve(claim.id(), ARUN, 1, sgd("20000"), "Total loss likely");

        var outcome = claimHandlingService.proposeDecision(claim.id(), ARUN, 2, Decision.Outcome.REJECT, null, "FRAUD_SUSPECTED");

        assertThat(outcome.status()).isEqualTo("DECISION_PENDING");
        assertThat(outcome.value().amount()).isEqualTo(sgd("20000"));
    }

    @Test
    void teamLeadApprovesButNotOwnAndNotAboveOwnLimit() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, 601));
        var pending = claimHandlingService.proposeDecision(claim.id(), ARUN, 1, Decision.Outcome.APPROVE, sgd("80000"), null);
        var decisionId = pending.value().id();

        expectCode(() -> claimHandlingService.reviewDecision(claim.id(), decisionId, ARUN, 2, ReviewAction.APPROVE, null),
                ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        expectCode(() -> claimHandlingService.reviewDecision(claim.id(), decisionId, KUMAR, 2, ReviewAction.APPROVE, null),
                ErrorCode.FORBIDDEN_ROLE);
        expectCode(() -> claimHandlingService.reviewDecision(claim.id(), decisionId, MEERA, 2, ReviewAction.APPROVE, null),
                ErrorCode.AMOUNT_EXCEEDS_AUTHORITY);
        var approved = claimHandlingService.reviewDecision(claim.id(), decisionId, LEE, 2, ReviewAction.APPROVE, null);

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(repository.written).extracting(e -> e.type()).contains(EventTypes.DECISION_APPROVED, EventTypes.CLAIM_APPROVED);
    }

    @Test
    void teamLeadCannotApproveTheirOwnProposal() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, 701));
        var pending = claimHandlingService.proposeDecision(claim.id(), MEERA, 1, Decision.Outcome.APPROVE, sgd("60000"), null);

        expectCode(() -> claimHandlingService.reviewDecision(claim.id(), pending.value().id(), MEERA, 2, ReviewAction.APPROVE, null),
                ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
    }

    @Test
    void returnSendsTheClaimBackForAssessment() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, 801));
        var pending = claimHandlingService.proposeDecision(claim.id(), ARUN, 1, Decision.Outcome.APPROVE, sgd("12000"), null);

        var returned = claimHandlingService.reviewDecision(claim.id(), pending.value().id(), MEERA, 2, ReviewAction.RETURN, "Get a second quote");

        assertThat(returned.status()).isEqualTo("UNDER_ASSESSMENT");
        expectCode(() -> claimHandlingService.reviewDecision(claim.id(), pending.value().id(), MEERA, 3, ReviewAction.APPROVE, null),
                ErrorCode.DECISION_NOT_FOUND);
    }

    @Test
    void staffContextRejectsCustomers() {
        assertThatThrownBy(() -> new StaffContext(ClaimFixture.PRIYA, Set.of(SG), null))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.FORBIDDEN_ROLE);
    }

    private static void expectCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(code);
    }
}
