package com.claims.platform.paymentsim;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** @param processingDelay how long the "bank" takes before answering (simulates cut-offs). */
@ConfigurationProperties(prefix = "payments.simulator")
public record SimulatorProperties(Duration processingDelay) {
    public SimulatorProperties {
        processingDelay = processingDelay == null ? Duration.ZERO : processingDelay;
    }
}
