package com.claims.platform.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/** The routing table: which service gets which path, and what is not exposed at all. */
class RoutingTest extends GatewayTestBase {

    @ParameterizedTest
    @CsvSource({
            // Notification Service
            "/claimant/v1/notifications,                         notification",
            "/claimant/v1/notifications/n-1/read,                notification",
            "/claimant/v1/notification-preferences,              notification",
            "/staff/v1/claims/c-1/notifications,                 notification",
            "/admin/v1/templates,                                notification",
            "/admin/v1/templates/t-1,                            notification",
            "/internal/v1/providers/email/delivery-status,       notification",
            // Insights Service
            "/staff/v1/work-queue,                               insights",
            "/staff/v1/search,                                   insights",
            "/staff/v1/approvals,                                insights",
            "/staff/v1/insights/workload,                        insights",
            // Claims Service
            "/claimant/v1/policies,                              claims",
            "/claimant/v1/uploads,                               claims",
            "/claimant/v1/claims,                                claims",
            "/claimant/v1/claims/c-1/timeline,                   claims",
            "/staff/v1/people,                                   claims",
            "/staff/v1/claims,                                   claims",
            "/staff/v1/claims/c-1,                               claims",
            "/staff/v1/claims/c-1/audit,                         claims",
            "/staff/v1/claims/c-1/notifications/extra,           claims",
            // Claimant portal
            "/,                                                  ui",
            "/claims/c-1,                                        ui",
            "/assets/index.js,                                   ui",
    })
    void routesEachPathToItsService(String path, String service) throws Exception {
        HttpResponse<String> response = get(path, tokenFor(path));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"service\":\"" + service + "\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/internal/v1/payments/callback",   // service-to-service only
            "/internal/anything",
            "/actuator/health",
            "/actuator/env",
            "/claimant/v2/claims",
            "/claimant/other",
            "/staff/v1/unknown",
            "/admin/v1/users",
            "/dev/other",
    })
    void unroutedApiPathsAre404AndNeverReachThePortal(String path) throws Exception {
        HttpResponse<String> response = get(path, tokenFor(path));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.body()).contains("\"code\":\"ROUTE_NOT_FOUND\"");
        assertThat(UI.getAllServeEvents()).isEmpty();
    }

    @Test
    void forwardsMethodPathQueryAndBodyUnchanged() throws Exception {
        get("/staff/v1/work-queue?view=mine&limit=20", token("staff-arun", "CLAIMS_ADJUSTER"));
        INSIGHTS.verify(getRequestedFor(urlEqualTo("/staff/v1/work-queue?view=mine&limit=20")));

        String claimant = token("cust-priya", "CUSTOMER");
        send("POST", "/claimant/v1/claims", claimant, "{\"policyNumber\":\"SG-MTR-POL-1001\"}",
                "Idempotency-Key", "key-1");
        CLAIMS.verify(postRequestedFor(urlEqualTo("/claimant/v1/claims"))
                .withRequestBody(equalToJson("{\"policyNumber\":\"SG-MTR-POL-1001\"}"))
                .withHeader("Idempotency-Key", equalTo("key-1"))
                .withHeader("Authorization", equalTo("Bearer " + claimant)));
    }

    @Test
    void loginIsRoutedToClaims() throws Exception {
        HttpResponse<String> response = send("POST", "/auth/login", null, "{\"username\":\"priya\",\"password\":\"ChangeMe123!\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        CLAIMS.verify(postRequestedFor(urlEqualTo("/auth/login")));
    }

    @Test
    void rejectsBodiesOverTheLimitBeforeTheyReachAService() throws Exception {
        String tooLarge = "{\"description\":\"" + "x".repeat(100_000) + "\"}";

        HttpResponse<String> response = send("POST", "/claimant/v1/claims", token("cust-priya", "CUSTOMER"), tooLarge);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
        assertThat(CLAIMS.getAllServeEvents()).isEmpty();
    }
}
