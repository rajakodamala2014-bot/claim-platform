package com.claims.platform.claims.platform.domain;

import com.claims.platform.claims.core.domain.MarketCode;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/** Per-market settings (platform.market_config). Markets are configuration: add a row, not code. */
public record MarketConfig(
        MarketCode market,
        String name,
        String currency,
        ZoneId timezone,
        int slaHours,
        int infoRequestDueDays,
        int coolingOffDays,
        List<String> languages,
        boolean enabled
) {
    public MarketConfig {
        languages = List.copyOf(languages);
    }

    public Duration slaTarget() {
        return Duration.ofHours(slaHours);
    }

    public Duration coolingOff() {
        return Duration.ofDays(coolingOffDays);
    }
}
