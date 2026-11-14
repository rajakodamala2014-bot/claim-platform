package com.claims.platform.identity.service;

import com.claims.platform.common.money.Money;
import com.claims.platform.common.security.AuthenticatedUser;
import com.claims.platform.identity.domain.AppUser;
import com.claims.platform.identity.domain.PolicySnapshot;
import com.claims.platform.identity.repo.AppUserRepository;
import com.claims.platform.identity.repo.PolicyRepository;
import com.claims.platform.identity.web.PolicyResponse;
import com.claims.platform.identity.web.PolicyResponse.Insured;
import com.claims.platform.web.ApiException;
import com.claims.platform.web.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class PolicyService {
    private final PolicyRepository policies;
    private final AppUserRepository users;

    public PolicyService(PolicyRepository policies, AppUserRepository users) {
        this.policies = policies;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PolicyResponse getByNumber(String policyNumber) {
        AuthenticatedUser caller = CurrentUser.get();
        PolicySnapshot policy = policies.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> ApiException.notFound("Policy not found"));
        authorize(caller, policy);
        return toResponse(policy);
    }

    @Transactional(readOnly = true)
    public PolicyList mine() {
        AuthenticatedUser caller = CurrentUser.get();
        List<PolicyResponse> items = policies.findByInsuredUserId(caller.userId()).stream()
                .map(this::toResponse)
                .toList();
        return new PolicyList(items);
    }

    private void authorize(AuthenticatedUser caller, PolicySnapshot policy) {
        if ("SERVICE".equals(caller.role())) {
            return;
        }
        if (caller.claimant()) {
            if (!caller.userId().equals(policy.getInsuredUserId())) {
                throw ApiException.notFound("Policy not found");
            }
            return;
        }
        if (caller.staff()) {
            if (!caller.hasMarket(policy.getMarketCode().trim())) {
                throw ApiException.forbidden("You are not assigned to market " + policy.getMarketCode());
            }
            return;
        }
        throw ApiException.forbidden("You are not allowed to read this policy");
    }

    private PolicyResponse toResponse(PolicySnapshot policy) {
        String name = users.findById(policy.getInsuredUserId()).map(AppUser::getFullName).orElse(null);
        List<String> perils = policy.getCoveredPerils() == null ? List.of() : Arrays.asList(policy.getCoveredPerils());
        return new PolicyResponse(
                policy.getPolicyId(),
                policy.getPolicyNumber(),
                policy.getMarketCode().trim(),
                policy.getProduct(),
                policy.getStatus(),
                policy.getCoverageStart(),
                policy.getCoverageEnd(),
                new Money(policy.getExcess(), policy.getCurrency().trim()),
                new Money(policy.getCoverLimit(), policy.getCurrency().trim()),
                perils,
                new Insured(policy.getInsuredUserId(), name)
        );
    }

    public record PolicyList(List<PolicyResponse> items) {
    }
}
