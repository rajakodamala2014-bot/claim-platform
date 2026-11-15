package com.claims.platform.claims.handling.cache.impl;

import com.claims.platform.claims.handling.cache.AuthorityLimitCache;
import com.claims.platform.claims.handling.entity.AuthorityPolicy;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/** In-process cache; the limits are re-read from the database once a minute. */
@Component
public class CaffeineAuthorityLimitCache implements AuthorityLimitCache {
    private static final String ALL_LIMITS = "all";

    private final Cache<String, AuthorityPolicy> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).build();

    @Override
    public AuthorityPolicy getAuthorityPolicy(Supplier<AuthorityPolicy> loader) {
        return cache.get(ALL_LIMITS, key -> loader.get());
    }
}
