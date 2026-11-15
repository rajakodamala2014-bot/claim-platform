package com.claims.platform.claims.intake.cache.impl;

import com.claims.platform.claims.intake.cache.PolicyCache;
import com.claims.platform.claims.intake.config.PolicySystemProperties;
import com.claims.platform.claims.intake.entity.PolicySnapshot;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** In-process cache; entries expire after {@code claims.policy-system.cache-ttl} (5 minutes). */
@Component
public class CaffeinePolicyCache implements PolicyCache {
    private static final int MAX_HOLDERS = 10_000;

    private final Cache<String, List<PolicySnapshot>> cache;

    public CaffeinePolicyCache(PolicySystemProperties properties) {
        this.cache = Caffeine.newBuilder().expireAfterWrite(properties.cacheTtl()).maximumSize(MAX_HOLDERS).build();
    }

    @Override
    public Optional<List<PolicySnapshot>> findPoliciesOfHolder(String holderId) {
        return Optional.ofNullable(cache.getIfPresent(holderId));
    }

    @Override
    public void putPoliciesOfHolder(String holderId, List<PolicySnapshot> policies) {
        cache.put(holderId, policies);
    }
}
