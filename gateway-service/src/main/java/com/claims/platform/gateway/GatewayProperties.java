package com.claims.platform.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.net.URI;
import java.time.Duration;

/**
 * @param devTokenRoute route {@code POST /dev/token} to claims-service (local token issuer; dev profile only)
 * @param maxBodySize   requests declaring a larger body are rejected with 413
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(Upstreams upstreams,
                                @DefaultValue("false") boolean devTokenRoute,
                                @DefaultValue("20MB") DataSize maxBodySize,
                                @DefaultValue RateLimit rateLimit,
                                @DefaultValue Breaker circuitBreaker,
                                @DefaultValue HttpClient httpClient) {

    public record Upstreams(URI claims, URI insights, URI notification, URI ui) {
    }

    /** Requests per minute per client: the token's {@code sub} when authenticated, otherwise the client IP. */
    public record RateLimit(@DefaultValue("true") boolean enabled,
                            @DefaultValue("300") int authenticated,
                            @DefaultValue("60") int anonymous) {
    }

    /** One breaker per upstream; it opens when {@code failureRateThreshold}% of the last {@code windowSize} calls failed. */
    public record Breaker(@DefaultValue("50") float failureRateThreshold,
                          @DefaultValue("20") int windowSize,
                          @DefaultValue("10") int minimumCalls,
                          @DefaultValue("10s") Duration openDuration,
                          @DefaultValue("3") int halfOpenCalls) {
    }

    public record HttpClient(@DefaultValue("2s") Duration connectTimeout,
                             @DefaultValue("30s") Duration readTimeout) {
    }
}
