package com.claims.platform.claims.core.entity;

import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.claims.platform.claims.core.entity.ClaimFixture.*;
import static com.claims.platform.claims.core.entity.ClaimStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every status-changing command, run against a claim in every status (12 x 9 = 108 cases). Where the command is
 * not allowed it must fail with CLAIM_INVALID_TRANSITION and leave the claim untouched: same status and
 * no events recorded. Commands run as a supervisor (or the owner) so only the state machine is under test.
 */
class ClaimCommandMatrixTest {

    record Command(String name, Set<ClaimStatus> allowedIn, Consumer<Claim> action) {
        @Override
        public String toString() {
            return name;
        }
    }

    static final List<Command> COMMANDS = List.of(
            new Command("confirmCover", EnumSet.of(SUBMITTED), c -> c.confirmCover(Actor.SYSTEM, at(500))),
            new Command("startAssessment", EnumSet.of(ASSIGNED), c -> c.startAssessment(MEERA, at(500))),
            new Command("requestInformation", EnumSet.of(UNDER_ASSESSMENT),
                    c -> c.requestInformation(UUID.randomUUID(), List.of("Photos"), at(10_000), MEERA, at(500))),
            new Command("proposeDecision", EnumSet.of(UNDER_ASSESSMENT),
                    c -> c.proposeDecision(UUID.randomUUID(), Decision.Outcome.APPROVE, sgd("100"), null,
                            ApprovalRequirement.SUPERVISOR_APPROVAL, MEERA, at(500))),
            new Command("instructSettlement", EnumSet.of(APPROVED),
                    c -> c.instructSettlement(UUID.randomUUID(), UUID.randomUUID(), PAYEE, sgd("100"), MEERA, at(500))),
            new Command("withdraw", EnumSet.of(SUBMITTED, TRIAGED, ASSIGNED, UNDER_ASSESSMENT, AWAITING_INFO),
                    c -> c.withdraw("No longer needed", PRIYA, at(500))),
            new Command("appeal", EnumSet.of(REJECTED), c -> c.appeal("New evidence", PRIYA, at(500))),
            new Command("close", EnumSet.of(SETTLED, REJECTED, WITHDRAWN), c -> c.close(at(500))),
            new Command("assign (first pick-up)", EnumSet.of(TRIAGED),
                    c -> c.assign("h-new", "team", MEERA, at(500)))
    );

    static Stream<Arguments> commandsByStatus() {
        return COMMANDS.stream().flatMap(cmd -> Arrays.stream(ClaimStatus.values()).map(s -> Arguments.of(cmd, s)));
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("commandsByStatus")
    void commandIsAllowedExactlyInItsStatuses(Command command, ClaimStatus status) {
        Claim claim = ClaimFixture.inStatus(status);
        assertThat(claim.status()).isEqualTo(status);

        boolean reassignment = command.name().startsWith("assign") && claim.handlerId().isPresent();
        if (command.allowedIn().contains(status) || (reassignment && !status.isOutcomeReached())) {
            assertThatCode(() -> command.action().accept(claim)).doesNotThrowAnyException();
            assertThat(claim.pendingEvents()).isNotEmpty();
        } else {
            assertThatThrownBy(() -> command.action().accept(claim))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.CLAIM_INVALID_TRANSITION);
            assertThat(claim.status()).isEqualTo(status);
            assertThat(claim.pendingEvents()).isEmpty();
        }
    }
}
