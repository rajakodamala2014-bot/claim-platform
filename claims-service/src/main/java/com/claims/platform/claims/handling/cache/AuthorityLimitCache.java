package com.claims.platform.claims.handling.cache;

import com.claims.platform.claims.handling.entity.AuthorityPolicy;

import java.util.function.Supplier;

/** Caches the configured authority limits: they change rarely, decisions are frequent. */
public interface AuthorityLimitCache {
    /** The cached policy, loaded with {@code loader} when absent or expired. */
    AuthorityPolicy getAuthorityPolicy(Supplier<AuthorityPolicy> loader);
}
