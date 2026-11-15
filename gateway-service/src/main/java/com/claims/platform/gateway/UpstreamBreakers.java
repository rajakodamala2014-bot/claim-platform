package com.claims.platform.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Set;

/**
 * One circuit breaker per upstream service, so a service that is down or timing out is answered immediately
 * with 503 instead of tying up gateway threads, and never affects the routes of the other services.
 * <p>
 * A call fails when the upstream cannot be reached or times out, or answers 502, 503 or 504. Client errors
 * (4xx) and 500s are the upstream's own answer and pass through untouched. Nothing is retried: not every POST
 * is idempotent.
 */
class UpstreamBreakers {
    private static final Logger log = LoggerFactory.getLogger(UpstreamBreakers.class);
    private static final Set<Integer> UPSTREAM_DOWN = Set.of(502, 503, 504);

    private final CircuitBreakerRegistry registry;

    UpstreamBreakers(GatewayProperties.Breaker settings) {
        this.registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.windowSize())
                .minimumNumberOfCalls(settings.minimumCalls())
                .failureRateThreshold(settings.failureRateThreshold())
                .waitDurationInOpenState(settings.openDuration())
                .permittedNumberOfCallsInHalfOpenState(settings.halfOpenCalls())
                .build());
    }

    CircuitBreaker breaker(String upstream) {
        return registry.circuitBreaker(upstream);
    }

    HandlerFilterFunction<ServerResponse, ServerResponse> filter(String upstream) {
        CircuitBreaker breaker = breaker(upstream);
        return (request, next) -> {
            if (!breaker.tryAcquirePermission()) {
                return unavailable(upstream);
            }
            long start = breaker.getCurrentTimestamp();
            ServerResponse response;
            try {
                response = next.handle(request);
            } catch (Exception e) {
                breaker.onError(breaker.getCurrentTimestamp() - start, breaker.getTimestampUnit(), e);
                log.warn("Upstream {} failed: {}", upstream, e.toString());
                return unavailable(upstream);
            }
            long elapsed = breaker.getCurrentTimestamp() - start;
            int status = response.statusCode().value();
            if (UPSTREAM_DOWN.contains(status)) {
                breaker.onError(elapsed, breaker.getTimestampUnit(),
                        new IllegalStateException("Upstream " + upstream + " answered " + status));
            } else {
                breaker.onSuccess(elapsed, breaker.getTimestampUnit());
            }
            return response;
        };
    }

    private static ServerResponse unavailable(String upstream) {
        return GatewayProblems.of(HttpStatus.SERVICE_UNAVAILABLE, GatewayProblems.UPSTREAM_UNAVAILABLE,
                "The " + upstream + " service is temporarily unavailable; try again shortly");
    }
}
