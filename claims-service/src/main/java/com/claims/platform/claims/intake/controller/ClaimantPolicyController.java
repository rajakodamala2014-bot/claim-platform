package com.claims.platform.claims.intake.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.claims.platform.claims.core.dto.response.MoneyResponse;
import com.claims.platform.claims.intake.dto.response.PolicyListResponse;
import com.claims.platform.claims.intake.dto.response.PolicyResponse;
import com.claims.platform.claims.intake.service.PolicyService;
import com.claims.platform.claims.intake.entity.PolicySnapshot;
import com.claims.platform.common.money.Money;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** C1: the customer's active policies (from the Policy system, cached 5 min). */
@RestController
@Tag(name = "Claimant policies", description = "The policies a customer can claim on")
public class ClaimantPolicyController {
    private final PolicyService policyService;

    public ClaimantPolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/claimant/v1/policies")
    @Operation(summary = "List my active policies", description = "C1. Read from the Policy system (cached for 5 minutes).")
    @ApiResponse(responseCode = "200", description = "The caller's active policies")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    @ApiResponse(responseCode = "503", description = "The Policy system is unavailable (POLICY_SYSTEM_UNAVAILABLE)")
    public PolicyListResponse listPolicies(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return new PolicyListResponse(policyService.listPoliciesOfHolder(jwt.getSubject()).stream().map(ClaimantPolicyController::view).toList());
    }

    private static PolicyResponse view(PolicySnapshot p) {
        return new PolicyResponse(p.policyNumber(), p.product(), p.lineOfBusiness().name(), p.market().value(),
                p.coverageStart(), p.coverageEnd(),
                p.excessMinor() == null ? null : MoneyResponse.of(Money.ofMinor(p.excessMinor(), p.currency())),
                p.coveredPerils());
    }
}
