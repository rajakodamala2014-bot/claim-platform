package com.claims.platform.claims.core.entity;

import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.exception.InvalidTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.claims.platform.claims.core.entity.ClaimStatus.*;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exhaustive: every (from, to) pair of the 12 statuses (144 cases) is checked against this independent copy
 * of the lifecycle from the specification, so an accidental edit of the production table fails here.
 */
class ClaimStatusTest {
    private static final Map<ClaimStatus, Set<ClaimStatus>> SPEC = Map.ofEntries(
            entry(SUBMITTED, EnumSet.of(TRIAGED, WITHDRAWN)),
            entry(TRIAGED, EnumSet.of(ASSIGNED, WITHDRAWN)),
            entry(ASSIGNED, EnumSet.of(UNDER_ASSESSMENT, WITHDRAWN)),
            entry(UNDER_ASSESSMENT, EnumSet.of(AWAITING_INFO, DECISION_PENDING, WITHDRAWN)),
            entry(AWAITING_INFO, EnumSet.of(UNDER_ASSESSMENT, WITHDRAWN)),
            entry(DECISION_PENDING, EnumSet.of(APPROVED, REJECTED, UNDER_ASSESSMENT)),
            entry(APPROVED, EnumSet.of(SETTLEMENT_IN_PROGRESS)),
            entry(REJECTED, EnumSet.of(CLOSED, UNDER_ASSESSMENT)),
            entry(SETTLEMENT_IN_PROGRESS, EnumSet.of(SETTLED)),
            entry(SETTLED, EnumSet.of(CLOSED)),
            entry(WITHDRAWN, EnumSet.of(CLOSED)),
            entry(CLOSED, EnumSet.noneOf(ClaimStatus.class)));

    static Stream<Arguments> allPairs() {
        return Arrays.stream(values()).flatMap(from -> Arrays.stream(values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void transitionTableMatchesSpecification(ClaimStatus from, ClaimStatus to) {
        boolean expected = SPEC.get(from).contains(to);

        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
        if (expected) {
            assertThatCode(() -> from.requireTransitionTo(to)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> from.requireTransitionTo(to))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessage("Claim cannot move from " + from + " to " + to)
                    .extracting(ex -> ((DomainException) ex).code()).isEqualTo(ErrorCode.CLAIM_INVALID_TRANSITION);
        }
    }

    @Test
    void specCoversEveryStatus() {
        assertThat(SPEC).containsOnlyKeys(values());
    }

    @Test
    void noStatusTransitionsToItself() {
        assertThat(values()).allSatisfy(s -> assertThat(s.canTransitionTo(s)).isFalse());
    }

    @Test
    void closedIsTheOnlyTerminalStatus() {
        assertThat(Arrays.stream(values()).filter(s -> s.allowedTargets().isEmpty())).containsExactly(CLOSED);
    }

    @Test
    void everyStatusIsReachableFromSubmitted() {
        Set<ClaimStatus> reached = EnumSet.of(SUBMITTED);
        boolean grew = true;
        while (grew) {
            Set<ClaimStatus> next = EnumSet.copyOf(reached);
            reached.forEach(s -> next.addAll(s.allowedTargets()));
            grew = next.size() > reached.size();
            reached = next;
        }
        assertThat(reached).containsExactlyInAnyOrder(values());
    }

    @Test
    void customerCanWithdrawOnlyBeforeADecision() {
        assertThat(Arrays.stream(values()).filter(ClaimStatus::isWithdrawable))
                .containsExactlyInAnyOrder(SUBMITTED, TRIAGED, ASSIGNED, UNDER_ASSESSMENT, AWAITING_INFO);
    }

    @Test
    void autoCloseAppliesToSettledRejectedAndWithdrawn() {
        assertThat(Arrays.stream(values()).filter(ClaimStatus::isAutoClosable))
                .containsExactlyInAnyOrder(SETTLED, REJECTED, WITHDRAWN);
    }
}
