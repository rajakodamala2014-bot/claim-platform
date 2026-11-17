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
            new AuthorityLimit(Role.CLAIMS_ADJUSTER, SG, MOTOR, Money.of("10000", "SGD")),
            new AuthorityLimit(Role.CLAIMS_ADJUSTER, SG, PROPERTY, Money.of("5000", "SGD")),
            new AuthorityLimit(Role.CLAIMS_ADJUSTER, HK, MOTOR, Money.of("80000", "HKD"))));

    @ParameterizedTest(name = "SG MOTOR {0} waits for another claim manager")
    @CsvSource({"0.01", "4200", "10000", "10000.01", "250000"})
    void everyAmountWaitsForAnotherClaimManager(String amount) {
        assertThat(policy.requirementFor(ARUN, SG, MOTOR, Money.of(amount, "SGD")))
                .isEqualTo(ApprovalRequirement.SUPERVISOR_APPROVAL);
    }

    @Test
    void theSameRuleAppliesInEveryMarketAndLine() {
        assertThat(policy.requirementFor(ARUN, SG, PROPERTY, Money.of("7000", "SGD")))
                .isEqualTo(ApprovalRequirement.SUPERVISOR_APPROVAL);
        assertThat(policy.requirementFor(ARUN, HK, MOTOR, Money.of("70000", "HKD")))
                .isEqualTo(ApprovalRequirement.SUPERVISOR_APPROVAL);
        assertThat(policy.requirementFor(ARUN, MarketCode.of("TH"), MOTOR, Money.of("1", "THB")))
                .isEqualTo(ApprovalRequirement.SUPERVISOR_APPROVAL);
        assertThat(policy.requirementFor(MEERA, SG, MOTOR, Money.of("10000", "SGD")))
                .isEqualTo(ApprovalRequirement.SUPERVISOR_APPROVAL);
        assertThat(policy.requirementFor(LEE, SG, MOTOR, Money.of("1", "SGD")))
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
    void anotherAdjusterCanApproveAnAmountAboveTheProposersCeiling() {
        Money twelveK = Money.of("12000", "SGD");

        assertThatCode(() -> policy.requireCanApprove(MEERA, SG, MOTOR, twelveK)).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCanApprove(ARUN, SG, MOTOR, twelveK)).doesNotThrowAnyException();
        expectCode(() -> policy.requireCanApprove(Actor.customer("c"), SG, MOTOR, twelveK), ErrorCode.FORBIDDEN_ROLE);
    }

    @Test
    void configurationIsValidated() {
        AuthorityLimit limit = new AuthorityLimit(Role.CLAIMS_ADJUSTER, SG, MOTOR, Money.of("1", "SGD"));

        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorityPolicy(List.of(limit, limit)))
                .withMessageContaining("Duplicate");
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorityLimit(Role.CUSTOMER, SG, MOTOR, Money.of("1", "SGD")));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorityLimit(Role.CLAIMS_ADJUSTER, SG, MOTOR, Money.of("-1", "SGD")));
        assertThat(policy.limitFor(Role.CLAIMS_ADJUSTER, SG, MOTOR)).contains(Money.of("10000", "SGD"));
    }

    private static void expectCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(code);
    }
}
