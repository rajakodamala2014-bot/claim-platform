package com.claims.platform.claims.platform.repository.impl;

import com.claims.platform.claims.platform.common.JdbcSupport;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.platform.repository.MarketConfigRepository;
import com.claims.platform.claims.platform.entity.MarketConfig;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.util.Optional;

@Repository
public class JdbcMarketConfigRepository implements MarketConfigRepository {
    private final JdbcClient jdbc;

    public JdbcMarketConfigRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MarketConfig> find(MarketCode market) {
        return jdbc.sql("SELECT * FROM platform.market_config WHERE market_code = :market")
                .param("market", market.value())
                .query((rs, n) -> new MarketConfig(MarketCode.of(rs.getString("market_code")), rs.getString("name"),
                        rs.getString("currency").trim(), ZoneId.of(rs.getString("timezone")), rs.getInt("sla_hours"),
                        rs.getInt("info_request_due_days"), rs.getInt("cooling_off_days"), JdbcSupport.strings(rs, "languages"),
                        rs.getBoolean("enabled")))
                .optional();
    }
}
