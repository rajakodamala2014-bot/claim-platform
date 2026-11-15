package com.claims.platform.claims.handling.entity;

import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.core.entity.Role;
import com.claims.platform.common.money.Money;

import java.util.Objects;

/** Highest amount a role may decide on its own in one market and line of business (authority_limit row). */
public record AuthorityLimit(Role role, MarketCode market, LineOfBusiness lineOfBusiness, Money maxAmount) {
    public AuthorityLimit {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(lineOfBusiness, "lineOfBusiness");
        Objects.requireNonNull(maxAmount, "maxAmount");
        if (!role.isStaff()) {
            throw new IllegalArgumentException("Authority limits apply to staff roles only, not " + role);
        }
        if (maxAmount.isNegative()) {
            throw new IllegalArgumentException("Authority limit cannot be negative");
        }
    }
}
