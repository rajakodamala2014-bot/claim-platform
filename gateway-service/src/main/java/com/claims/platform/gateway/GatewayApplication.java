package com.claims.platform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * The API gateway: the only public entry point. It routes by path to the claims, insights and notification
 * services (and the claimant portal), validates the bearer token, rate-limits per client, isolates a failing
 * upstream behind a circuit breaker and normalises headers. Services still validate tokens themselves.
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
