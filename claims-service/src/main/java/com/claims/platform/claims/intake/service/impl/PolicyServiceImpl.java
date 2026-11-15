package com.claims.platform.claims.intake.service.impl;

import com.claims.platform.claims.intake.client.PolicyClient;
import com.claims.platform.claims.intake.entity.PolicySnapshot;
import com.claims.platform.claims.intake.service.PolicyService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PolicyServiceImpl implements PolicyService {
    private final PolicyClient policies;

    public PolicyServiceImpl(PolicyClient policies) {
        this.policies = policies;
    }

    @Override
    public List<PolicySnapshot> listPoliciesOfHolder(String claimantId) {
        return policies.fetchPoliciesOfHolder(claimantId).stream().filter(PolicySnapshot::isActive).toList();
    }
}
