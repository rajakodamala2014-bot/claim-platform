package com.claims.platform.claims.core.entity;

import com.claims.platform.common.money.Money;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds claims in any status by driving the real aggregate through its public commands (never by poking
 * fields), so every fixture is a reachable state. Pending events are cleared, so a test only sees the
 * events of the command under test.
 */
public final class ClaimFixture {
    public static final Instant T0 = Instant.parse("2026-11-14T02:00:00Z");
    public static final Duration SLA = Duration.ofHours(48);

    public static final Actor PRIYA = Actor.claimant("cust-priya");
    public static final Actor OTHER_CUSTOMER = Actor.claimant("cust-other");
    public static final Actor ARUN = Actor.handler("h-arun");
    public static final Actor KUMAR = Actor.handler("h-kumar");
    public static final Actor MEERA = Actor.teamLead("tl-meera");
    public static final Actor DAVID = Actor.teamLead("tl-david");
    public static final Actor LEE = Actor.manager("m-lee");

    public static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-00000000001a");
    public static final UUID DECISION_ID = UUID.fromString("00000000-0000-0000-0000-00000000001d");
    public static final UUID SETTLEMENT_ID = UUID.fromString("00000000-0000-0000-0000-00000000005e");
    public static final UUID INSTRUCTION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final Payee PAYEE = new Payee("Priya Nair", "DBSSSGSG", "123456789");

    private ClaimFixture() {
    }

    /** The constant id for the default claim (sequence 123); a distinct, deterministic id for other sequences. */
    public static UUID idFor(UUID base, long seq) {
        return seq == 123 ? base : new UUID(seq, base.getLeastSignificantBits());
    }

    public static Money sgd(String major) {
        return Money.of(major, "SGD");
    }

    public static Instant at(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    public static NewClaim newClaim(Channel channel) {
        return newClaim(channel, 123);
    }

    public static NewClaim newClaim(Channel channel, long sequence) {
        return new NewClaim(ClaimId.newId(),
                ClaimNumber.of(MarketCode.of("SG"), LineOfBusiness.MOTOR, 2026, sequence),
                MarketCode.of("SG"), LineOfBusiness.MOTOR, "SG-MTR-POL-1001", PRIYA.id(), channel, "SGD",
                Instant.parse("2026-11-13T09:30:00Z"), sgd("6000"), SLA);
    }

    public static Claim submitted() {
        return submitted(123);
    }

    public static Claim submitted(long sequence) {
        return Claim.submit(newClaim(Channel.APP, sequence), PRIYA, T0);
    }

    /** A claim in {@code status}, reached through the happy path (or the nearest branch). */
    public static Claim inStatus(ClaimStatus status) {
        return inStatus(status, 123);
    }

    /** As {@link #inStatus(ClaimStatus)} with a distinct claim number, for tests that persist several claims. */
    public static Claim inStatus(ClaimStatus status, long sequence) {
        Claim claim = build(status, sequence);
        claim.pullPendingEvents();
        return claim;
    }

    private static Claim build(ClaimStatus status, long seq) {
        return switch (status) {
            case SUBMITTED -> submitted(seq);
            case ASSIGNED -> {
                Claim c = build(ClaimStatus.SUBMITTED, seq);
                c.confirmCover(Actor.SYSTEM, at(1));
                c.assign(ARUN.id(), "team-sg-motor", ARUN, at(10));
                yield c;
            }
            case UNDER_ASSESSMENT -> {
                Claim c = build(ClaimStatus.ASSIGNED, seq);
                c.startAssessment(ARUN, at(20));
                c.changeReserve(sgd("5000"), "Initial estimate", ARUN, at(21));
                yield c;
            }
            case AWAITING_INFO -> {
                Claim c = build(ClaimStatus.UNDER_ASSESSMENT, seq);
                c.requestInformation(idFor(REQUEST_ID, seq), List.of("Police report", "Repair quote"), at(60 * 24 * 7), ARUN, at(30));
                yield c;
            }
            case DECISION_PENDING -> {
                Claim c = build(ClaimStatus.UNDER_ASSESSMENT, seq);
                c.proposeDecision(idFor(DECISION_ID, seq), Decision.Outcome.APPROVE, sgd("12000"), null,
                        ApprovalRequirement.SUPERVISOR_APPROVAL, ARUN, at(40));
                yield c;
            }
            case APPROVED -> {
                Claim c = build(ClaimStatus.UNDER_ASSESSMENT, seq);
                c.proposeDecision(idFor(DECISION_ID, seq), Decision.Outcome.APPROVE, sgd("4200"), null,
                        ApprovalRequirement.NONE, ARUN, at(40));
                yield c;
            }
            case REJECTED -> {
                Claim c = build(ClaimStatus.UNDER_ASSESSMENT, seq);
                c.proposeDecision(idFor(DECISION_ID, seq), Decision.Outcome.REJECT, sgd("5000"), "NOT_COVERED_PERIL",
                        ApprovalRequirement.NONE, ARUN, at(40));
                yield c;
            }
            case SETTLEMENT_IN_PROGRESS -> {
                Claim c = build(ClaimStatus.APPROVED, seq);
                c.instructSettlement(idFor(SETTLEMENT_ID, seq), idFor(INSTRUCTION_ID, seq), PAYEE, sgd("4200"), ARUN, at(50));
                yield c;
            }
            case SETTLED -> {
                Claim c = build(ClaimStatus.SETTLEMENT_IN_PROGRESS, seq);
                c.confirmPayment(idFor(SETTLEMENT_ID, seq), idFor(INSTRUCTION_ID, seq), "PAY-1", at(55), at(55));
                yield c;
            }
            case WITHDRAWN -> {
                Claim c = build(ClaimStatus.SUBMITTED, seq);
                c.withdraw("Settled privately with the other driver", PRIYA, at(5));
                yield c;
            }
            case CLOSED -> {
                Claim c = build(ClaimStatus.SETTLED, seq);
                c.close(at(60 * 24 * 15));
                yield c;
            }
        };
    }
}
