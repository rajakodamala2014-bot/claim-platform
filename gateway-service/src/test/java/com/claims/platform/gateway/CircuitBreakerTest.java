package com.claims.platform.gateway;

import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

/** One breaker per upstream: a failing service is cut off quickly and the others keep working. */
@TestPropertySource(properties = {
        "gateway.circuit-breaker.window-size=2",
        "gateway.circuit-breaker.minimum-calls=2",
        "gateway.circuit-breaker.open-duration=1h"
})
class CircuitBreakerTest extends GatewayTestBase {
    private final String handler = token("staff-arun", "CLAIMS_ADJUSTER");

    @Test
    void anUnreachableUpstreamIsAnsweredWith503ProblemJson() throws Exception {
        INSIGHTS.stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        HttpResponse<String> response = get("/staff/v1/work-queue", handler);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.body()).contains("\"code\":\"UPSTREAM_UNAVAILABLE\"").contains("insights");
    }

    @Test
    void repeatedFailuresOpenTheBreakerAndStopCallingTheUpstream() throws Exception {
        INSIGHTS.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(503).withBody("insights is down")));

        // while closed, the upstream's own 503 is passed through
        assertThat(get("/staff/v1/work-queue", handler).body()).isEqualTo("insights is down");
        assertThat(get("/staff/v1/search", handler).body()).isEqualTo("insights is down");
        assertThat(breakers.breaker(Routes.INSIGHTS).getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // open: answered by the gateway, the upstream is left alone
        HttpResponse<String> shortCircuited = get("/staff/v1/approvals", handler);
        assertThat(shortCircuited.statusCode()).isEqualTo(503);
        assertThat(shortCircuited.body()).contains("\"code\":\"UPSTREAM_UNAVAILABLE\"");
        assertThat(INSIGHTS.getAllServeEvents()).hasSize(2);
    }

    @Test
    void anOpenBreakerDoesNotAffectTheOtherServices() throws Exception {
        INSIGHTS.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(503)));
        get("/staff/v1/work-queue", handler);
        get("/staff/v1/work-queue", handler);
        assertThat(get("/staff/v1/work-queue", handler).statusCode()).isEqualTo(503);

        assertThat(get("/staff/v1/claims/c-1", handler).statusCode()).isEqualTo(200);
        assertThat(get("/staff/v1/claims/c-1/notifications", handler).statusCode()).isEqualTo(200);
        assertThat(breakers.breaker(Routes.CLAIMS).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void clientErrorsAndServerErrorsFromTheUpstreamPassThroughAndDoNotOpenTheBreaker() throws Exception {
        CLAIMS.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"code\":\"CLAIM_INVALID_TRANSITION\"}")));
        for (int i = 0; i < 4; i++) {
            HttpResponse<String> response = get("/staff/v1/claims/c-1", handler);
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.body()).isEqualTo("{\"code\":\"CLAIM_INVALID_TRANSITION\"}");
        }

        CLAIMS.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(500).withBody("boom")));
        for (int i = 0; i < 4; i++) {
            assertThat(get("/staff/v1/claims/c-1", handler).statusCode()).isEqualTo(500);
        }

        assertThat(breakers.breaker(Routes.CLAIMS).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
