package com.claims.platform.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Central token validation: what the gateway rejects never reaches a service. */
class GatewaySecurityTest extends GatewayTestBase {

    @ParameterizedTest
    @ValueSource(strings = {
            "/claimant/v1/claims", "/claimant/v1/notifications", "/staff/v1/claims/c-1", "/staff/v1/work-queue",
            "/admin/v1/templates"
    })
    void apiRequestsWithoutATokenAre401(String path) throws Exception {
        HttpResponse<String> response = get(path, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertNothingReachedAService();
    }

    @Test
    void invalidOrForeignTokensAre401() throws Exception {
        assertThat(get("/claimant/v1/claims", "not-a-jwt").statusCode()).isEqualTo(401);

        String tampered = token("cust-priya", "CLAIMANT");
        tampered = tampered.substring(0, tampered.length() - 4) + "AAAA";
        assertThat(get("/claimant/v1/claims", tampered).statusCode()).isEqualTo(401);
        assertNothingReachedAService();
    }

    @ParameterizedTest
    @CsvSource({
            "/staff/v1/claims/c-1,     CLAIMANT",   // customers can't use the staff surface
            "/staff/v1/work-queue,     CLAIMANT",
            "/claimant/v1/claims,      HANDLER",    // staff can't use the claimant surface
            "/claimant/v1/claims,      MANAGER",
            "/admin/v1/templates,      HANDLER",    // templates are managers only
            "/admin/v1/templates,      TEAM_LEAD",
    })
    void wrongRoleForTheSurfaceIs403(String path, String role) throws Exception {
        HttpResponse<String> response = get(path, token("someone", role));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
        assertNothingReachedAService();
    }

    @ParameterizedTest
    @CsvSource({
            "/claimant/v1/claims,                  CLAIMANT",
            "/staff/v1/claims/c-1,                 HANDLER",
            "/staff/v1/approvals,                  TEAM_LEAD",
            "/staff/v1/insights/exposure,          MANAGER",
            "/admin/v1/templates,                  MANAGER",
    })
    void rightRoleIsLetThrough(String path, String role) throws Exception {
        assertThat(get(path, token("someone", role)).statusCode()).isEqualTo(200);
    }

    @Test
    void signatureAuthorisedEndpointsNeedNoToken() throws Exception {
        // pre-signed upload URL (HMAC checked by claims-service)
        assertThat(send("PUT", "/claimant/v1/uploads/u-1/content?expires=1&signature=abc", null, "{}").statusCode())
                .isEqualTo(200);
        // provider delivery receipts (HMAC checked by notification-service)
        assertThat(send("POST", "/internal/v1/providers/email/delivery-status", null, "{}").statusCode())
                .isEqualTo(200);
        // the upload exception is for PUT on that exact path only
        assertThat(get("/claimant/v1/uploads/u-1/content", null).statusCode()).isEqualTo(401);
        assertThat(send("PUT", "/claimant/v1/uploads/u-1", null, "{}").statusCode()).isEqualTo(401);
    }

    @Test
    void thePortalNeedsNoToken() throws Exception {
        assertThat(get("/", null).body()).contains("ui");
    }

    private static void assertNothingReachedAService() {
        assertThat(CLAIMS.getAllServeEvents()).isEmpty();
        assertThat(INSIGHTS.getAllServeEvents()).isEmpty();
        assertThat(NOTIFICATION.getAllServeEvents()).isEmpty();
    }
}
