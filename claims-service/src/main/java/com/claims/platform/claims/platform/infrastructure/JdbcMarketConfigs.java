package com.claims.platform.claims.platform.infrastructure;

import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.platform.application.MarketConfigs;
import com.claims.platform.claims.platform.domain.MarketConfig;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.util.Optional;

@Repository
public class JdbcMarketConfigs implements MarketConfigs {
    private final JdbcClient jdbc;

    public JdbcMarketConfigs(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MarketConfig> find(MarketCode market) {
        return jdbc.sql("SELECT * FROM platform.market_config WHERE market_code = :market")
                .param("market", market.value())
                .query((rs, n) -> new MarketConfig(MarketCode.of(rs.getString("market_code")), rs.getString("name"),
                        rs.getString("currency").trim(), ZoneId.of(rs.getString("timezone")), rs.getInt("sla_hours"),
                        rs.getInt("info_request_due_days"), rs.getInt("cooling_off_days"), Jdbc.strings(rs, "languages"),
                        rs.getBoolean("enabled")))
                .optional();
    }
}
