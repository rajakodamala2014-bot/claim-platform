package com.claims.platform.claims.intake.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param baseUrl  Policy admin system
 * @param timeout  connect + read timeout per call (spec: 2 s)
 * @param cacheTtl how long a holder's policy list is cached (spec: 5 min)
 */
@ConfigurationProperties(prefix = "claims.policy-system")
public record PolicySystemProperties(String baseUrl, Duration timeout, Duration cacheTtl) {
    public PolicySystemProperties {
        baseUrl = baseUrl == null ? "http://localhost:8089" : baseUrl.replaceAll("/+$", "");
        timeout = timeout == null ? Duration.ofSeconds(2) : timeout;
        cacheTtl = cacheTtl == null ? Duration.ofMinutes(5) : cacheTtl;
    }
}
