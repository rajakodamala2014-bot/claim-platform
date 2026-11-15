package com.claims.platform.claims.intake.cache;

import com.claims.platform.claims.intake.entity.PolicySnapshot;

import java.util.List;
import java.util.Optional;

/** Short-lived cache of the policies of a holder, so the Policy system is not asked on every request. */
public interface PolicyCache {
    Optional<List<PolicySnapshot>> findPoliciesOfHolder(String holderId);

    void putPoliciesOfHolder(String holderId, List<PolicySnapshot> policies);
}
