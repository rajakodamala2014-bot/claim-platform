package com.claims.platform.gateway;

import com.claims.platform.web.problem.Problems;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Per-client token buckets, refilled every minute. The client is the token's {@code sub} when the request is
 * authenticated, otherwise the caller's IP address (token issuer, signed uploads, provider callbacks).
 * <p>
 * Buckets live in this instance's memory: with several gateway instances each enforces its own quota. To share
 * quotas, replace the cache with a distributed Bucket4j proxy manager; nothing else changes.
 */
class RateLimiter implements HandlerFilterFunction<ServerResponse, ServerResponse> {
    private final GatewayProperties.RateLimit limits;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(200_000)
            .build();

    RateLimiter(GatewayProperties.RateLimit limits) {
        this.limits = limits;
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        if (!limits.enabled()) {
            return next.handle(request);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication instanceof JwtAuthenticationToken;
        String client = authenticated ? "sub:" + authentication.getName()
                : "ip:" + request.servletRequest().getRemoteAddr();
        int perMinute = authenticated ? limits.authenticated() : limits.anonymous();

        ConsumptionProbe probe = buckets.get(client, key -> newBucket(perMinute)).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return next.handle(request);
        }
        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
        return GatewayProblems.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(Problems.of(HttpStatus.TOO_MANY_REQUESTS, GatewayProblems.RATE_LIMITED,
                        "Too many requests; retry after " + retryAfterSeconds + " s"));
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }
}
