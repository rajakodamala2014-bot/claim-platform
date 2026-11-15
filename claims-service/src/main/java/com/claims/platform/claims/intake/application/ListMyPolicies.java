package com.claims.platform.claims.intake.application;

import com.claims.platform.claims.intake.domain.PolicySnapshot;

import java.util.List;

/** C1: the customer's active policies for the "Report an incident" form. */
public class ListMyPolicies {
    private final PolicyGateway policies;

    public ListMyPolicies(PolicyGateway policies) {
        this.policies = policies;
    }

    public List<PolicySnapshot> list(String claimantId) {
        return policies.policiesOf(claimantId).stream().filter(PolicySnapshot::isActive).toList();
    }
}
