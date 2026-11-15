package com.claims.platform.gateway;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/** Request/response rewriting at the edge: correlation ids, forwarded headers, response hardening. */
class EdgeHeadersTest extends GatewayTestBase {
    private final String claimant = token("cust-priya", "CLAIMANT");

    @Test
    void mintsACorrelationIdForwardsItAndEchoesItOnce() throws Exception {
        HttpResponse<String> response = get("/claimant/v1/claims", claimant);

        assertThat(response.headers().allValues("X-Correlation-Id")).hasSize(1);
        String id = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(id).isNotBlank();
        CLAIMS.verify(getRequestedFor(urlEqualTo("/claimant/v1/claims")).withHeader("X-Correlation-Id", equalTo(id)));
    }

    @Test
    void keepsTheCallersCorrelationIdEvenWhenTheUpstreamEchoesIt() throws Exception {
        CLAIMS.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("X-Correlation-Id", "mobile-app-42")));

        HttpResponse<String> response = get("/claimant/v1/claims", claimant, "X-Correlation-Id", "mobile-app-42");

        assertThat(response.headers().allValues("X-Correlation-Id")).containsExactly("mobile-app-42");
        CLAIMS.verify(getRequestedFor(anyUrl()).withHeader("X-Correlation-Id", equalTo("mobile-app-42")));
    }

    @Test
    void replacesACorrelationIdThatIsNotSafeToLog() throws Exception {
        HttpResponse<String> response = get("/claimant/v1/claims", claimant, "X-Correlation-Id", "bad id {}");

        String id = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(id).isNotEqualTo("bad id {}").matches("[A-Za-z0-9._-]{1,64}");
        CLAIMS.verify(getRequestedFor(anyUrl()).withHeader("X-Correlation-Id", equalTo(id)));
    }

    @Test
    void writesItsOwnForwardedHeadersAndDiscardsTheCallers() throws Exception {
        get("/claimant/v1/claims", claimant,
                "X-Forwarded-For", "203.0.113.9",
                "X-Forwarded-Proto", "https",
                "X-Forwarded-Host", "evil.example",
                "X-Forwarded-Prefix", "/spoofed",
                "Forwarded", "for=203.0.113.9");

        CLAIMS.verify(getRequestedFor(anyUrl())
                .withHeader("X-Forwarded-For", notMatching(".*203\\.0\\.113\\.9.*"))
                .withHeader("X-Forwarded-Proto", equalTo("http"))
                .withHeader("X-Forwarded-Host", equalTo("localhost:" + port))
                .withHeader("X-Forwarded-Prefix", absent())
                .withHeader("Forwarded", absent()));
    }

    @Test
    void apiResponsesAreNotCacheableOrSniffable() throws Exception {
        CLAIMS.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Cache-Control", "public, max-age=3600")
                .withHeader("ETag", "\"7\"")));

        HttpResponse<String> response = get("/claimant/v1/claims/c-1", claimant);

        assertThat(response.headers().allValues("Cache-Control")).containsExactly("no-store");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        // headers the API contract relies on are untouched
        assertThat(response.headers().firstValue("ETag")).hasValue("\"7\"");
    }

    @Test
    void portalResponsesKeepTheirOwnCachingHeaders() throws Exception {
        UI.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Cache-Control", "public, max-age=31536000, immutable")));

        HttpResponse<String> response = get("/assets/index.js", null);

        assertThat(response.headers().firstValue("Cache-Control")).hasValue("public, max-age=31536000, immutable");
    }

    @Test
    void gatewayErrorsCarryTheCorrelationId() throws Exception {
        HttpResponse<String> response = get("/claimant/v1/claims", null, "X-Correlation-Id", "trace-7");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"correlationId\":\"trace-7\"");
    }
}
