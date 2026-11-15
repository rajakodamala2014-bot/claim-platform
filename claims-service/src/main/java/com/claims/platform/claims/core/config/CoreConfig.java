package com.claims.platform.claims.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** The clock every service uses, so tests can fix the time. */
@Configuration
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
