package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.core.application.InMemoryClaimRepository;
import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.Actor;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimFixture;
import com.claims.platform.claims.core.domain.ClaimStatus;
import com.claims.platform.claims.core.domain.Decision;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.core.domain.Role;
import com.claims.platform.claims.handling.domain.AuthorityLimit;
import com.claims.platform.claims.handling.domain.AuthorityPolicy;
import com.claims.platform.common.event.EventTypes;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static com.claims.platform.claims.core.domain.ClaimFixture.sgd;
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
    private final StaffClaims staffClaims = new StaffClaims(repository, InMemoryClaimRepository.directUnitOfWork());
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC);
    private final AuthorityLimits limits = () -> new AuthorityPolicy(List.of(
            new AuthorityLimit(Role.HANDLER, SG, LineOfBusiness.MOTOR, sgd("10000")),
            new AuthorityLimit(Role.TEAM_LEAD, SG, LineOfBusiness.MOTOR, sgd("50000")),
            new AuthorityLimit(Role.MANAGER, SG, LineOfBusiness.MOTOR, sgd("500000"))));
    private final AssignClaim assign = new AssignClaim(staffClaims, clock);
    private final ProposeDecision propose = new ProposeDecision(staffClaims, limits, clock);
    private final ReviewDecision review = new ReviewDecision(staffClaims, limits, clock);

    @Test
    void pickUpDefaultsToTheCallerAndTheirTeamAndBumpsTheVersion() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.TRIAGED));

        var outcome = assign.assign(claim.id(), ARUN, 1, null, null);

        assertThat(outcome.value()).isEqualTo("h-arun");
        assertThat(outcome.status()).isEqualTo("ASSIGNED");
        assertThat(outcome.version()).isEqualTo(2);
        assertThat(claim.teamId()).contains("team-sg-motor");
    }

    @Test
    void secondPickUpIsAlreadyAssignedAndStaleVersionsAreRejected() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.TRIAGED));
        assign.assign(claim.id(), ARUN, 1, null, null);

        expectCode(() -> assign.assign(claim.id(), KUMAR, 2, null, null), ErrorCode.CLAIM_ALREADY_ASSIGNED);
        expectCode(() -> assign.assign(claim.id(), MEERA, 1, "h-kumar", null), ErrorCode.STALE_VERSION);
        assertThat(claim.handlerId()).contains("h-arun");
    }

    @Test
    void foreignMarketIsForbiddenBeforeTheVersionIsChecked() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.TRIAGED));

        expectCode(() -> assign.assign(claim.id(), MY_HANDLER, 999, null, null), ErrorCode.FORBIDDEN_MARKET);
        expectCode(() -> staffClaims.loadVisible(claim.id(), MY_HANDLER), ErrorCode.FORBIDDEN_MARKET);
        expectCode(() -> staffClaims.loadVisible(com.claims.platform.claims.core.domain.ClaimId.newId(), ARUN),
                ErrorCode.CLAIM_NOT_FOUND);
    }

    @Test
    void withinAuthorityIsFinalAboveGoesToATeamLead() {
        Claim small = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, 501));
        Claim large = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, 502));

        var final1 = propose.propose(small.id(), ARUN, 1, Decision.Outcome.APPROVE, sgd("10000"), null);
        var pending = propose.propose(large.id(), ARUN, 1, Decision.Outcome.APPROVE, sgd("10000.01"), null);

        assertThat(final1.status()).isEqualTo("APPROVED");
        assertThat(final1.value().status()).isEqualTo(Decision.Status.FINAL);
        assertThat(pending.status()).isEqualTo("DECISION_PENDING");
        assertThat(pending.value().status()).isEqualTo(Decision.Status.AWAITING_APPROVAL);
    }

    @Test
    void rejectionAuthorityUsesTheOutstandingReserve() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT)); // reserve 5,000
        new SetReserve(staffClaims, clock)
                .set(claim.id(), ARUN, 1, sgd("20000"), "Total loss likely");

        var outcome = propose.propose(claim.id(), ARUN, 2, Decision.Outcome.REJECT, null, "FRAUD_SUSPECTED");

        assertThat(outcome.status()).isEqualTo("DECISION_PENDING");
        assertThat(outcome.value().amount()).isEqualTo(sgd("20000"));
    }

    @Test
    void teamLeadApprovesButNotOwnAndNotAboveOwnLimit() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, 601));
        var pending = propose.propose(claim.id(), ARUN, 1, Decision.Outcome.APPROVE, sgd("80000"), null);
        var decisionId = pending.value().id();

        expectCode(() -> review.review(claim.id(), decisionId, ARUN, 2, ReviewDecision.Action.APPROVE, null),
                ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        expectCode(() -> review.review(claim.id(), decisionId, KUMAR, 2, ReviewDecision.Action.APPROVE, null),
                ErrorCode.FORBIDDEN_ROLE);
        expectCode(() -> review.review(claim.id(), decisionId, MEERA, 2, ReviewDecision.Action.APPROVE, null),
                ErrorCode.AMOUNT_EXCEEDS_AUTHORITY);
        var approved = review.review(claim.id(), decisionId, LEE, 2, ReviewDecision.Action.APPROVE, null);

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(repository.written).extracting(e -> e.type()).contains(EventTypes.DECISION_APPROVED, EventTypes.CLAIM_APPROVED);
    }

    @Test
    void teamLeadCannotApproveTheirOwnProposal() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, 701));
        var pending = propose.propose(claim.id(), MEERA, 1, Decision.Outcome.APPROVE, sgd("60000"), null);

        expectCode(() -> review.review(claim.id(), pending.value().id(), MEERA, 2, ReviewDecision.Action.APPROVE, null),
                ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
    }

    @Test
    void returnSendsTheClaimBackForAssessment() {
        Claim claim = repository.add(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT, 801));
        var pending = propose.propose(claim.id(), ARUN, 1, Decision.Outcome.APPROVE, sgd("12000"), null);

        var returned = review.review(claim.id(), pending.value().id(), MEERA, 2, ReviewDecision.Action.RETURN, "Get a second quote");

        assertThat(returned.status()).isEqualTo("UNDER_ASSESSMENT");
        expectCode(() -> review.review(claim.id(), pending.value().id(), MEERA, 3, ReviewDecision.Action.APPROVE, null),
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
