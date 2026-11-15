package com.claims.platform.claims.platform.application;

import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.platform.domain.MarketConfig;

import java.util.Optional;

public interface MarketConfigs {

    Optional<MarketConfig> find(MarketCode market);

    default MarketConfig requireEnabled(MarketCode market) {
        return find(market).filter(MarketConfig::enabled)
                .orElseThrow(() -> new DomainException(ErrorCode.MARKET_NOT_SUPPORTED, "Market " + market + " is not supported"));
    }
}
