package com.claims.platform.claims.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled (outbox relay, background jobs). Individual jobs have their own enable flags. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
