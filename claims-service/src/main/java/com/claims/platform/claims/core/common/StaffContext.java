package com.claims.platform.claims.core.common;

import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.entity.MarketCode;

import java.util.Objects;
import java.util.Set;

/** A staff caller: who they are, which markets they may work in (token {@code markets}), their team. */
public record StaffContext(Actor actor, Set<MarketCode> markets, String teamId) {
    public StaffContext {
        Objects.requireNonNull(actor, "actor");
        markets = Set.copyOf(markets);
        if (!actor.role().isStaff()) {
            throw new DomainException(ErrorCode.FORBIDDEN_ROLE, "Staff role required");
        }
    }

    public void requireMarket(MarketCode market) {
        if (!markets.contains(market)) {
            throw new DomainException(ErrorCode.FORBIDDEN_MARKET, "You are not assigned to market " + market);
        }
    }
}
