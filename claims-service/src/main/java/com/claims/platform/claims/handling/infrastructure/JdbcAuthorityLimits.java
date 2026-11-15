package com.claims.platform.claims.handling.infrastructure;

import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.core.domain.Role;
import com.claims.platform.claims.handling.application.AuthorityLimits;
import com.claims.platform.claims.handling.domain.AuthorityLimit;
import com.claims.platform.claims.handling.domain.AuthorityPolicy;
import com.claims.platform.common.money.Money;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/** Reads limits from the database; cached for a minute (limits change rarely, decisions are frequent). */
@Repository
public class JdbcAuthorityLimits implements AuthorityLimits {
    private final JdbcClient jdbc;
    private final LoadingCache<String, AuthorityPolicy> cache;

    public JdbcAuthorityLimits(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).build(key -> load());
    }

    @Override
    public AuthorityPolicy currentPolicy() {
        return cache.get("all");
    }

    private AuthorityPolicy load() {
        return new AuthorityPolicy(jdbc.sql("SELECT role, market_code, line_of_business, max_amount_minor, currency "
                        + "FROM handling.authority_limit")
                .query((rs, n) -> new AuthorityLimit(Role.valueOf(rs.getString("role")),
                        MarketCode.of(rs.getString("market_code")), LineOfBusiness.valueOf(rs.getString("line_of_business")),
                        Money.ofMinor(rs.getLong("max_amount_minor"), rs.getString("currency").trim())))
                .list());
    }
}
