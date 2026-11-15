package com.claims.platform.claims.handling.repository.impl;

import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.core.entity.Role;
import com.claims.platform.claims.handling.cache.AuthorityLimitCache;
import com.claims.platform.claims.handling.repository.AuthorityLimitRepository;
import com.claims.platform.claims.handling.entity.AuthorityLimit;
import com.claims.platform.claims.handling.entity.AuthorityPolicy;
import com.claims.platform.common.money.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;


/** Reads the limits from handling.authority_limit, through the {@link AuthorityLimitCache}. */
@Repository
public class JdbcAuthorityLimitRepository implements AuthorityLimitRepository {
    private final JdbcClient jdbc;
    private final AuthorityLimitCache cache;

    public JdbcAuthorityLimitRepository(JdbcClient jdbc, AuthorityLimitCache cache) {
        this.jdbc = jdbc;
        this.cache = cache;
    }

    @Override
    public AuthorityPolicy loadAuthorityPolicy() {
        return cache.getAuthorityPolicy(this::readAuthorityPolicy);
    }

    private AuthorityPolicy readAuthorityPolicy() {
        return new AuthorityPolicy(jdbc.sql("SELECT role, market_code, line_of_business, max_amount_minor, currency "
                        + "FROM handling.authority_limit")
                .query((rs, n) -> new AuthorityLimit(Role.valueOf(rs.getString("role")),
                        MarketCode.of(rs.getString("market_code")), LineOfBusiness.valueOf(rs.getString("line_of_business")),
                        Money.ofMinor(rs.getLong("max_amount_minor"), rs.getString("currency").trim())))
                .list());
    }
}
