package com.claims.platform.claims.handling.application;

import com.claims.platform.claims.handling.domain.AuthorityPolicy;

/** Source of the configured authority limits (handling.authority_limit). */
public interface AuthorityLimits {
    AuthorityPolicy currentPolicy();
}
