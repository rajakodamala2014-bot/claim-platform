package com.claims.platform.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Runs the real gateway on a random port in front of four WireMock servers standing in for the claims,
 * insights and notification services and the portal. Each one answers every request with its own name, so a
 * test can tell where a request was routed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "management.server.port=0",
        "gateway.max-body-size=64KB",
        // effectively off, so only RateLimitTest (which lowers them) ever sees a 429
        "gateway.rate-limit.authenticated=1000000",
        "gateway.rate-limit.anonymous=1000000"
})
abstract class GatewayTestBase {
    private static final String DEV_SECRET = "local-dev-only-secret-change-me-0123456789abcdef";

    static final WireMockServer CLAIMS = started();
    static final WireMockServer INSIGHTS = started();
    static final WireMockServer NOTIFICATION = started();
    static final WireMockServer UI = started();

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    @Autowired
    UpstreamBreakers breakers;

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        registry.add("gateway.upstreams.claims", CLAIMS::baseUrl);
        registry.add("gateway.upstreams.insights", INSIGHTS::baseUrl);
        registry.add("gateway.upstreams.notification", NOTIFICATION::baseUrl);
        registry.add("gateway.upstreams.ui", UI::baseUrl);
    }

    @BeforeEach
    void resetUpstreams() {
        answerWithName(CLAIMS, Routes.CLAIMS);
        answerWithName(INSIGHTS, Routes.INSIGHTS);
        answerWithName(NOTIFICATION, Routes.NOTIFICATION);
        answerWithName(UI, "ui");
        for (String upstream : List.of(Routes.CLAIMS, Routes.INSIGHTS, Routes.NOTIFICATION)) {
            breakers.breaker(upstream).reset();
        }
    }

    private static WireMockServer started() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        return server;
    }

    private static void answerWithName(WireMockServer server, String name) {
        server.resetAll();
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"service\":\"" + name + "\"}")));
    }

    /** A token as the dev issuer (and the shared decoder) define it: HS256, {@code roles} claim. */
    static String token(String subject, String... roles) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(DEV_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("claims-dev-idp")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("roles", List.of(roles))
                .claim("markets", List.of("SG"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** A token whose role is allowed on the surface the path belongs to (or none for token-less paths). */
    static String tokenFor(String path) {
        if (path.startsWith("/claimant/")) {
            return token("cust-priya", "CUSTOMER");
        }
        if (path.startsWith("/staff/")) {
            return token("staff-arun", "CLAIMS_ADJUSTER");
        }
        if (path.startsWith("/admin/")) {
            return token("staff-lee", "CLAIMS_ADJUSTER");
        }
        return null;
    }

    HttpResponse<String> get(String path, String token, String... headers) throws Exception {
        return send("GET", path, token, null, headers);
    }

    HttpResponse<String> send(String method, String path, String token, String body, String... headers)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (headers.length > 0) {
            request.headers(headers);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
