package com.claims.platform.claims.platform.repository;

import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.platform.entity.MarketConfig;

import java.util.Optional;

public interface MarketConfigRepository {

    Optional<MarketConfig> find(MarketCode market);

    default MarketConfig requireEnabled(MarketCode market) {
        return find(market).filter(MarketConfig::enabled)
                .orElseThrow(() -> new DomainException(ErrorCode.MARKET_NOT_SUPPORTED, "Market " + market + " is not supported"));
    }
}
