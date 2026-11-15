package com.claims.platform.claims.intake.api;

import com.claims.platform.claims.core.application.ClaimantViews.MoneyView;
import com.claims.platform.claims.intake.application.ListMyPolicies;
import com.claims.platform.claims.intake.domain.PolicySnapshot;
import com.claims.platform.common.money.Money;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** C1: the customer's active policies (from the Policy system, cached 5 min). */
@RestController
public class ClaimantPolicyController {
    private final ListMyPolicies listMyPolicies;

    public ClaimantPolicyController(ListMyPolicies listMyPolicies) {
        this.listMyPolicies = listMyPolicies;
    }

    public record PolicyView(String policyNumber, String product, String lineOfBusiness, String market,
                             LocalDate coverageStart, LocalDate coverageEnd, MoneyView excess, List<String> coveredPerils) {
    }

    public record PolicyList(List<PolicyView> items) {
    }

    @GetMapping("/claimant/v1/policies")
    public PolicyList policies(@AuthenticationPrincipal Jwt jwt) {
        return new PolicyList(listMyPolicies.list(jwt.getSubject()).stream().map(ClaimantPolicyController::view).toList());
    }

    private static PolicyView view(PolicySnapshot p) {
        return new PolicyView(p.policyNumber(), p.product(), p.lineOfBusiness().name(), p.market().value(),
                p.coverageStart(), p.coverageEnd(),
                p.excessMinor() == null ? null : MoneyView.of(Money.ofMinor(p.excessMinor(), p.currency())),
                p.coveredPerils());
    }
}
