package com.claims.platform.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Per-client rate limiting. Each test uses its own subjects, because buckets live as long as the context. */
@TestPropertySource(properties = {
        "gateway.rate-limit.authenticated=3",
        "gateway.rate-limit.anonymous=2"
})
class RateLimitTest extends GatewayTestBase {

    @Test
    void aClientOverItsLimitGets429WithRetryAfter() throws Exception {
        String token = token("cust-burst", "CUSTOMER");
        for (int i = 0; i < 3; i++) {
            assertThat(get("/claimant/v1/claims", token).statusCode()).isEqualTo(200);
        }

        HttpResponse<String> limited = get("/claimant/v1/claims", token);

        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(limited.body()).contains("\"code\":\"RATE_LIMITED\"");
        long retryAfter = Long.parseLong(limited.headers().firstValue("Retry-After").orElseThrow());
        assertThat(retryAfter).isBetween(1L, 60L);
        assertThat(CLAIMS.getAllServeEvents()).hasSize(3);
    }

    @Test
    void theLimitIsPerClientAndSharedAcrossServices() throws Exception {
        String arun = token("staff-arun-rl", "CLAIMS_ADJUSTER");
        String kumar = token("staff-kumar-rl", "CLAIMS_ADJUSTER");

        // Arun spends his budget across two different services
        assertThat(get("/staff/v1/claims/c-1", arun).statusCode()).isEqualTo(200);
        assertThat(get("/staff/v1/work-queue", arun).statusCode()).isEqualTo(200);
        assertThat(get("/staff/v1/claims/c-1/notifications", arun).statusCode()).isEqualTo(200);
        assertThat(get("/staff/v1/work-queue", arun).statusCode()).isEqualTo(429);

        // Kumar is unaffected
        assertThat(get("/staff/v1/work-queue", kumar).statusCode()).isEqualTo(200);
    }

    @Test
    void anonymousCallsAreLimitedByClientAddressAndThePortalIsNot() throws Exception {
        assertThat(send("POST", "/auth/login", null, "{}").statusCode()).isEqualTo(200);
        assertThat(send("POST", "/auth/login", null, "{}").statusCode()).isEqualTo(200);
        assertThat(send("POST", "/auth/login", null, "{}").statusCode()).isEqualTo(429);

        for (int i = 0; i < 10; i++) {
            assertThat(get("/assets/index.js", null).statusCode()).isEqualTo(200);
        }
    }
}
