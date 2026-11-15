package com.claims.platform.claims.intake.client.impl;

import com.claims.platform.claims.intake.cache.impl.CaffeinePolicyCache;
import com.claims.platform.claims.intake.config.PolicySystemProperties;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.intake.entity.CoverCheck;
import com.claims.platform.claims.intake.entity.PolicySnapshot;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Policy system adapter against a real HTTP server: contract, timeouts, caching, failure mapping. */
class RestPolicyClientTest {
    private static final String POLICIES = """
            {"items":[{"policyNumber":"SG-MTR-POL-1001","holderId":"cust-priya","holderName":"Priya Nair",
              "marketCode":"SG","lineOfBusiness":"MOTOR","product":"Motor Comprehensive","status":"ACTIVE",
              "coverageStart":"2026-01-01","coverageEnd":"2026-12-31","currency":"SGD","sumInsuredMinor":3000000,
              "excessMinor":50000,"coveredPerils":["COLLISION","THEFT"],"vehicle":{"registration":"SBA1234X"}}]}
            """;

    private WireMockServer policySystem;
    private RestPolicyClient policyClient;

    @BeforeEach
    void start() {
        policySystem = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        policySystem.start();
        PolicySystemProperties properties = new PolicySystemProperties(policySystem.baseUrl(), Duration.ofMillis(1500),
                Duration.ofMinutes(5));
        policyClient = new RestPolicyClient(RestClient.builder(), properties, new CaffeinePolicyCache(properties));
    }

    @AfterEach
    void stop() {
        policySystem.stop();
    }

    @Test
    void mapsPoliciesIgnoringUnknownFieldsAndCachesThem() {
        policySystem.stubFor(get(urlPathEqualTo("/api/v1/policies")).withQueryParam("holderId", equalTo("cust-priya"))
                .willReturn(okJson(POLICIES)));

        List<PolicySnapshot> first = policyClient.fetchPoliciesOfHolder("cust-priya");
        List<PolicySnapshot> second = policyClient.fetchPoliciesOfHolder("cust-priya");

        assertThat(first).singleElement().satisfies(p -> {
            assertThat(p.policyNumber()).isEqualTo("SG-MTR-POL-1001");
            assertThat(p.lineOfBusiness()).isEqualTo(LineOfBusiness.MOTOR);
            assertThat(p.market().value()).isEqualTo("SG");
            assertThat(p.coverageEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(p.coveredPerils()).containsExactly("COLLISION", "THEFT");
        });
        assertThat(second).isEqualTo(first);
        policySystem.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/policies")));
    }

    @Test
    void slowPolicySystemTimesOutAsUnavailable() {
        policySystem.stubFor(get(urlPathEqualTo("/api/v1/policies")).willReturn(okJson(POLICIES).withFixedDelay(3000)));

        long started = System.nanoTime();
        assertThatThrownBy(() -> policyClient.fetchPoliciesOfHolder("cust-priya")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.POLICY_SYSTEM_UNAVAILABLE);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(2800));
    }

    @Test
    void serverErrorIsUnavailableAndNotCached() {
        policySystem.stubFor(get(urlPathEqualTo("/api/v1/policies")).willReturn(serverError()));

        assertThatThrownBy(() -> policyClient.fetchPoliciesOfHolder("cust-priya")).isInstanceOf(DomainException.class);

        policySystem.stubFor(get(urlPathEqualTo("/api/v1/policies")).willReturn(okJson(POLICIES)));
        assertThat(policyClient.fetchPoliciesOfHolder("cust-priya")).hasSize(1);
    }

    @Test
    void coverCheckSendsIncidentDetailsAndMapsTheAnswer() {
        policySystem.stubFor(post("/api/v1/policies/SG-MTR-POL-1001/cover-checks")
                .withRequestBody(equalToJson("{\"incidentDate\":\"2026-11-13\",\"peril\":\"COLLISION\",\"lineOfBusiness\":\"MOTOR\"}"))
                .willReturn(okJson("{\"covered\":true,\"reason\":null}")));
        policySystem.stubFor(post("/api/v1/policies/SG-MTR-POL-1999/cover-checks")
                .willReturn(okJson("{\"covered\":false,\"reason\":\"POLICY_NOT_IN_FORCE\"}")));

        assertThat(policyClient.checkCover("SG-MTR-POL-1001", LineOfBusiness.MOTOR, LocalDate.of(2026, 11, 13), "COLLISION"))
                .isInstanceOf(CoverCheck.Covered.class);
        assertThat(policyClient.checkCover("SG-MTR-POL-1999", LineOfBusiness.MOTOR, LocalDate.of(2026, 11, 13), "COLLISION"))
                .isEqualTo(new CoverCheck.NotCovered("POLICY_NOT_IN_FORCE"));
    }

    @Test
    void coverCheckFailuresNeverThrow() {
        policySystem.stubFor(post("/api/v1/policies/SLOW/cover-checks").willReturn(okJson("{\"covered\":true}").withFixedDelay(3000)));
        policySystem.stubFor(post("/api/v1/policies/BROKEN/cover-checks").willReturn(serviceUnavailable()));
        policySystem.stubFor(post("/api/v1/policies/GONE/cover-checks").willReturn(notFound()));

        LocalDate day = LocalDate.of(2026, 11, 13);
        assertThat(policyClient.checkCover("SLOW", LineOfBusiness.MOTOR, day, "FIRE")).isInstanceOf(CoverCheck.Unavailable.class);
        assertThat(policyClient.checkCover("BROKEN", LineOfBusiness.MOTOR, day, "FIRE")).isInstanceOf(CoverCheck.Unavailable.class);
        assertThat(policyClient.checkCover("GONE", LineOfBusiness.MOTOR, day, "FIRE")).isEqualTo(new CoverCheck.NotCovered("POLICY_NOT_FOUND"));
    }

    @Test
    void connectionRefusedIsUnavailable() {
        policySystem.stop();

        assertThatThrownBy(() -> policyClient.fetchPoliciesOfHolder("cust-priya")).isInstanceOf(DomainException.class);
        assertThat(policyClient.checkCover("X", LineOfBusiness.MOTOR, LocalDate.now(), "FIRE")).isInstanceOf(CoverCheck.Unavailable.class);
    }
}
