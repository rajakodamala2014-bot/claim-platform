package com.claims.platform.insights.entity;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClaimSummaryTest {
    private static final Instant T0 = Instant.parse("2026-11-14T02:00:00Z");
    private static final UUID CLAIM = UUID.randomUUID();

    @Test
    void submittedCreatesTheRowWithSlaAndCurrency() {
        ClaimSummary s = submitted();

        assertThat(s.status()).isEqualTo("SUBMITTED");
        assertThat(s.coverStatus()).isEqualTo("PENDING");
        assertThat(s.claimNumber()).isEqualTo("SG-MTR-2026-000001");
        assertThat(s.slaDueAt()).isEqualTo(T0.plus(Duration.ofHours(48)));
        assertThat(s.currency()).isEqualTo("SGD");
        assertThat(s.isOpen()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> ClaimSummary.fromSubmitted(event(EventTypes.CLAIM_TRIAGED, 1, Map.of())));
    }

    @Test
    void foldsTheWholeHappyPath() {
        ClaimSummary s = submitted();
        s.apply(event(EventTypes.CLAIM_TRIAGED, 1, Map.of("coverStatus", "CONFIRMED")));
        s.apply(event(EventTypes.CLAIM_ASSIGNED, 2, Map.of("handlerId", "h-arun", "teamId", "team-sg-motor")));
        assertThat(s.status()).isEqualTo("ASSIGNED");
        s.apply(event(EventTypes.ASSESSMENT_STARTED, 3, Map.of()));
        s.apply(event(EventTypes.RESERVE_CHANGED, 4, Map.of("newOutstandingMinor", 500_000, "currency", "SGD")));
        assertThat(s.reserveOutstandingMinor()).isEqualTo(500_000);

        s.apply(event(EventTypes.INFORMATION_REQUESTED, 5, Map.of()));
        assertThat(s.status()).isEqualTo("AWAITING_INFO");
        assertThat(s.isSlaRunning()).isFalse();
        Instant extended = T0.plus(Duration.ofHours(50));
        s.apply(event(EventTypes.INFORMATION_PROVIDED, 125, Map.of("slaDueAt", extended.toString())));
        assertThat(s.isSlaRunning()).isTrue();
        assertThat(s.slaDueAt()).isEqualTo(extended);

        s.apply(event(EventTypes.DECISION_PROPOSED, 130, Map.of("decisionId", UUID.randomUUID().toString(), "outcome", "APPROVE",
                "amountMinor", 1_200_000, "approvalRequired", true, "actorId", "h-arun")));
        assertThat(s.status()).isEqualTo("DECISION_PENDING");
        assertThat(s.pendingDecision()).get().satisfies(d -> {
            assertThat(d.amountMinor()).isEqualTo(1_200_000);
            assertThat(d.proposedBy()).isEqualTo("h-arun");
        });

        s.apply(event(EventTypes.DECISION_APPROVED, 131, Map.of()));
        s.apply(event(EventTypes.CLAIM_APPROVED, 131, Map.of("approvedAmountMinor", 1_200_000)));
        assertThat(s.pendingDecision()).isEmpty();
        assertThat(s.decidedAt()).isEqualTo(at(131));
        assertThat(s.isSlaRunning()).isFalse();

        s.apply(event(EventTypes.SETTLEMENT_INSTRUCTED, 132, Map.of()));
        s.apply(event(EventTypes.PAYMENT_CONFIRMED, 133, Map.of()));
        s.apply(event(EventTypes.CLAIM_SETTLED, 133, Map.of()));
        s.apply(event(EventTypes.RESERVE_CHANGED, 133, Map.of("newOutstandingMinor", 0, "currency", "SGD")));
        assertThat(s.status()).isEqualTo("SETTLED");
        assertThat(s.isOpen()).isFalse();
        assertThat(s.reserveOutstandingMinor()).isZero();

        s.apply(event(EventTypes.CLAIM_CLOSED, 500, Map.of()));
        assertThat(s.status()).isEqualTo("CLOSED");
        assertThat(s.closedAt()).isEqualTo(at(500));
        assertThat(s.lastEventAt()).isEqualTo(at(500));
    }

    @Test
    void decisionWithinAuthorityNeverReachesTheInbox() {
        ClaimSummary s = submitted();
        s.apply(event(EventTypes.DECISION_PROPOSED, 10, Map.of("decisionId", UUID.randomUUID().toString(), "outcome", "APPROVE",
                "amountMinor", 100, "approvalRequired", false, "actorId", "h-arun")));

        assertThat(s.pendingDecision()).isEmpty();
    }

    @Test
    void returnedDecisionLeavesTheInboxAndReopenCountsAppeals() {
        ClaimSummary s = submitted();
        s.apply(event(EventTypes.DECISION_PROPOSED, 10, Map.of("decisionId", UUID.randomUUID().toString(), "outcome", "REJECT",
                "amountMinor", 100, "approvalRequired", true, "actorId", "h-arun")));
        s.apply(event(EventTypes.DECISION_RETURNED, 11, Map.of()));
        assertThat(s.pendingDecision()).isEmpty();
        assertThat(s.status()).isEqualTo("UNDER_ASSESSMENT");

        s.apply(event(EventTypes.CLAIM_REJECTED, 12, Map.of()));
        s.apply(event(EventTypes.CLAIM_REOPENED, 13, Map.of()));
        assertThat(s.reopenCount()).isEqualTo(1);
        assertThat(s.decidedAt()).isNull();
        assertThat(s.status()).isEqualTo("UNDER_ASSESSMENT");
    }

    @Test
    void coverDeclinedIsFlaggedAndUnknownEventsAreIgnored() {
        ClaimSummary s = submitted();
        s.apply(event(EventTypes.COVER_DECLINED, 1, Map.of("reason", "POLICY_NOT_IN_FORCE")));
        s.apply(event(EventTypes.CLAIM_TRIAGED, 1, Map.of("coverStatus", "DECLINED")));
        s.apply(event("SomethingNewInV2", 2, Map.of()));

        assertThat(s.coverStatus()).isEqualTo("DECLINED");
        assertThat(s.status()).isEqualTo("SUBMITTED");
    }

    @ParameterizedTest
    @CsvSource({
            "true,  10, PAUSED,   false",
            "false, 10, ON_TRACK, true",
            "false, 3,  AT_RISK,  true",
            "false, 0,  BREACHED, true",
            "false, -5, BREACHED, true",
    })
    void slaState(boolean paused, int hoursUntilDue, SlaState expected, boolean running) {
        Instant now = T0;
        assertThat(SlaState.of(now.plus(Duration.ofHours(hoursUntilDue)), !paused && running, now, Duration.ofHours(4)))
                .isEqualTo(expected);
    }

    private static ClaimSummary submitted() {
        Map<String, Object> data = new HashMap<>(Map.of("claimNumber", "SG-MTR-2026-000001", "policyNumber", "SG-MTR-POL-1001",
                "claimantId", "cust-priya", "channel", "APP", "currency", "SGD",
                "slaDueAt", T0.plus(Duration.ofHours(48)).toString()));
        return ClaimSummary.fromSubmitted(new EventEnvelope(UUID.randomUUID(), EventTypes.CLAIM_SUBMITTED, 1, T0, CLAIM,
                "SG", "MOTOR", null, data));
    }

    private static EventEnvelope event(String type, int minutes, Map<String, Object> data) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, at(minutes), CLAIM, "SG", "MOTOR", null, data);
    }

    private static Instant at(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }
}
