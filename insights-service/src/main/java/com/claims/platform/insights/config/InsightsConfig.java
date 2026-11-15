package com.claims.platform.insights.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** The clock and scheduling (SLA alert check). */
@Configuration
@EnableScheduling
public class InsightsConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
