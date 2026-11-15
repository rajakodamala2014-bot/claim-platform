package com.claims.platform.claims.handling.repository;

import com.claims.platform.claims.handling.entity.AuthorityPolicy;

/** Source of the configured authority limits (handling.authority_limit). */
public interface AuthorityLimitRepository {
    AuthorityPolicy loadAuthorityPolicy();
}
