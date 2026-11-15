package com.claims.platform.claims.handling.entity;

import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.ApprovalRequirement;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.core.entity.Role;
import com.claims.platform.common.money.Money;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.claims.platform.claims.core.entity.LineOfBusiness.MOTOR;
import static com.claims.platform.claims.core.entity.LineOfBusiness.PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorityPolicyTest {
    private static final MarketCode SG = MarketCode.of("SG");
    private static final MarketCode HK = MarketCode.of("HK");
    private static final Actor ARUN = Actor.handler("h-arun");
    private static final Actor MEERA = Actor.teamLead("tl-meera");
    private static final Actor LEE = Actor.manager("m-lee");

    private final AuthorityPolicy policy = new AuthorityPolicy(List.of(
            new AuthorityLimit(Role.HANDLER, SG, MOTOR, Money.of("10000", "SGD")),
            new AuthorityLimit(Role.HANDLER, SG, PROPERTY, Money.of("5000", "SGD")),
            new AuthorityLimit(Role.TEAM_LEAD, SG, MOTOR, Money.of("50000", "SGD")),
            new AuthorityLimit(Role.MANAGER, SG, MOTOR, Money.of("1000000", "SGD")),
            new AuthorityLimit(Role.HANDLER, HK, MOTOR, Money.of("80000", "HKD"))));

    @ParameterizedTest(name = "handler SG MOTOR {0} -> {1}")
    @CsvSource({
            "0.01,     NONE",
            "4200,     NONE",
            "9999.99,  NONE",
            "10000,    NONE",                 // limit is inclusive
            "10000.01, SUPERVISOR_APPROVAL",  // one cent above
            "250000,   SUPERVISOR_APPROVAL",
    })
    void handlerLimitBoundaries(String amount, ApprovalRequirement expected) {
        assertThat(policy.requirementFor(ARUN, SG, MOTOR, Money.of(amount, "SGD"))).isEqualTo(expected);
    }

    @Test
    void limitsAreSpecificToMarketAndLineOfBusiness() {
        Money sevenK = Money.of("7000", "SGD");

        assertThat(policy.requirementFor(ARUN, SG, MOTOR, sevenK)).isEqualTo(ApprovalRequirement.NONE);
        assertThat(policy.requirementFor(ARUN, SG, PROPERTY, sevenK)).isEqualTo(ApprovalRequirement.SUPERVISOR_APPROVAL);
        assertThat(policy.requirementFor(ARUN, HK, MOTOR, Money.of("70000", "HKD"))).isEqualTo(ApprovalRequirement.NONE);
    }

    @Test
    void missingLimitMeansApprovalIsNeeded() {
        assertThat(policy.requirementFor(ARUN, MarketCode.of("TH"), MOTOR, Money.of("1", "THB")))
                .isEqualTo(ApprovalRequirement.SUPERVISOR_APPROVAL);
        assertThat(policy.requirementFor(MEERA, SG, PROPERTY, Money.of("1", "SGD")))
                .isEqualTo(ApprovalRequirement.SUPERVISOR_APPROVAL);
    }

    @Test
    void teamLeadProposalsUseTheTeamLeadLimit() {
        assertThat(policy.requirementFor(MEERA, SG, MOTOR, Money.of("50000", "SGD"))).isEqualTo(ApprovalRequirement.NONE);
        assertThat(policy.requirementFor(MEERA, SG, MOTOR, Money.of("50000.01", "SGD")))
                .isEqualTo(ApprovalRequirement.SUPERVISOR_APPROVAL);
    }

    @Test
    void customersCannotPropose() {
        expectCode(() -> policy.requirementFor(Actor.claimant("c"), SG, MOTOR, Money.of("1", "SGD")), ErrorCode.FORBIDDEN_ROLE);
    }

    @Test
    void amountInAnotherCurrencyThanTheLimitIsRejected() {
        expectCode(() -> policy.requirementFor(ARUN, SG, MOTOR, Money.of("1", "HKD")), ErrorCode.CURRENCY_MISMATCH);
    }

    @Test
    void approverNeedsSupervisorRoleAndALimitCoveringTheAmount() {
        Money twelveK = Money.of("12000", "SGD");
        Money eightyK = Money.of("80000", "SGD");

        assertThatCode(() -> policy.requireCanApprove(MEERA, SG, MOTOR, twelveK)).doesNotThrowAnyException();
        expectCode(() -> policy.requireCanApprove(MEERA, SG, MOTOR, eightyK), ErrorCode.AMOUNT_EXCEEDS_AUTHORITY);
        assertThatCode(() -> policy.requireCanApprove(LEE, SG, MOTOR, eightyK)).doesNotThrowAnyException();
        expectCode(() -> policy.requireCanApprove(ARUN, SG, MOTOR, twelveK), ErrorCode.FORBIDDEN_ROLE);
        expectCode(() -> policy.requireCanApprove(MEERA, SG, PROPERTY, Money.of("1", "SGD")), ErrorCode.AMOUNT_EXCEEDS_AUTHORITY);
    }

    @Test
    void configurationIsValidated() {
        AuthorityLimit limit = new AuthorityLimit(Role.HANDLER, SG, MOTOR, Money.of("1", "SGD"));

        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorityPolicy(List.of(limit, limit)))
                .withMessageContaining("Duplicate");
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorityLimit(Role.CLAIMANT, SG, MOTOR, Money.of("1", "SGD")));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorityLimit(Role.HANDLER, SG, MOTOR, Money.of("-1", "SGD")));
        assertThat(policy.limitFor(Role.HANDLER, SG, MOTOR)).contains(Money.of("10000", "SGD"));
    }

    private static void expectCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(code);
    }
}
