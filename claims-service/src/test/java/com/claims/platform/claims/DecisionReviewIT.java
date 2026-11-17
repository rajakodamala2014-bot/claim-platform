package com.claims.platform.claims;

import com.claims.platform.claims.settlement.controller.PaymentCallbackController;
import com.claims.platform.common.web.Headers;
import com.claims.platform.testsupport.TestKafka;
import com.claims.platform.testsupport.TestPostgres;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The decision branches not covered by {@link StaffJourneyIT}, over real HTTP and PostgreSQL 16: a rejection within
 * the handler's limit is final at once, the customer appeals it (ClaimReopened), a later approval above the limit is
 * returned by the team lead for more assessment; and the payments webhook refuses unsigned or forged results.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DecisionReviewIT {
    private static final WireMockServer POLICY_SYSTEM = new WireMockServer(WireMockConfiguration.options()
            .dynamicPort().usingFilesUnderDirectory("../deployment/docker/policy-system"));

    static {
        POLICY_SYSTEM.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        TestPostgres.registerFreshDatabase(registry);
        TestKafka.register(registry, ClaimsTopics.ALL);
        registry.add("claims.policy-system.base-url", POLICY_SYSTEM::baseUrl);
        registry.add("claims.documents.storage-dir", Files.createTempDirectory("claims-docs")::toString);
    }

    @AfterAll
    static void stopPolicySystem() {
        POLICY_SYSTEM.stop();
    }

    @Autowired
    TestRestTemplate http;
    @Autowired
    ObjectMapper json;

    @Test
    void rejectionWaitsForAnotherManagerThenTheCustomerCanAppeal() throws Exception {
        String priya = token("priya"), arun = token("arun"), meera = token("meera");
        String claim = submitAsCustomer(priya);
        String base = "/staff/v1/claims/" + claim;

        String v = command(base + "/assignment", arun, "1", null, HttpStatus.OK);
        v = command(base + "/start-assessment", arun, v, null, HttpStatus.OK);
        // a rejection needs a reason
        expect(HttpMethod.POST, base + "/decisions", arun, v, Map.of("outcome", "REJECT"), HttpStatus.BAD_REQUEST);

        // A rejection is final at once. It does not wait for another claim manager.
        JsonNode rejected = expect(HttpMethod.POST, base + "/decisions", arun, v,
                Map.of("outcome", "REJECT", "reasonCode", "POLICY_EXCLUSION"), HttpStatus.OK);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.at("/result/approvalRequired").asBoolean()).isFalse();
        JsonNode customerView = customer(HttpMethod.GET, "/claimant/v1/claims/" + claim, priya, null, HttpStatus.OK);
        assertThat(customerView.get("status").asText()).isEqualTo("REJECTED");

        // C10 the customer appeals: a reason is required, then the claim is reopened for the same handler
        customer(HttpMethod.POST, "/claimant/v1/claims/" + claim + "/appeal", priya, Map.of("reason", ""), HttpStatus.BAD_REQUEST);
        assertThat(customer(HttpMethod.POST, "/claimant/v1/claims/" + claim + "/appeal", priya,
                Map.of("reason", "The exclusion does not apply"), HttpStatus.OK).get("status").asText()).isEqualTo("UNDER_ASSESSMENT");
        customer(HttpMethod.POST, "/claimant/v1/claims/" + claim + "/appeal", priya,
                Map.of("reason", "again"), HttpStatus.CONFLICT);
        assertThat(customer(HttpMethod.GET, "/claimant/v1/claims/" + claim + "/timeline", priya, null, HttpStatus.OK))
                .extracting(e -> e.get("type").asText()).contains("ClaimRejected", "ClaimReopened");

        ResponseEntity<String> reopened = exchange(HttpMethod.GET, base, arun, null, null);
        JsonNode workspace = json.readTree(reopened.getBody());
        assertThat(workspace.at("/claim/status").asText()).isEqualTo("UNDER_ASSESSMENT");
        assertThat(workspace.at("/claim/handlerId").asText()).isEqualTo("h-arun");
        v = etag(reopened);

        // reassessed: the covered amount waits for another claim manager
        JsonNode proposed = expect(HttpMethod.POST, base + "/decisions", arun, v,
                Map.of("outcome", "APPROVE", "coverage", "PARTIAL", "amount", money("12000.00")), HttpStatus.ACCEPTED);
        assertThat(proposed.get("status").asText()).isEqualTo("DECISION_PENDING");
        String decisionId = proposed.at("/result/decisionId").asText();
        v = String.valueOf(proposed.get("version").asLong());

        // ... and a stale version is refused before anything changes
        expect(HttpMethod.POST, base + "/decisions/" + decisionId + "/approval", meera, "1",
                Map.of("action", "RETURN"), HttpStatus.PRECONDITION_FAILED);

        // S10 the team lead returns it for more assessment, saying why
        expect(HttpMethod.POST, base + "/decisions/" + decisionId + "/approval", meera, v,
                Map.of("action", "RETURN"), HttpStatus.BAD_REQUEST);
        JsonNode returned = expect(HttpMethod.POST, base + "/decisions/" + decisionId + "/approval", meera, v,
                Map.of("action", "RETURN", "comment", "Need the repair invoice"), HttpStatus.OK);
        assertThat(returned.get("status").asText()).isEqualTo("UNDER_ASSESSMENT");
        v = String.valueOf(returned.get("version").asLong());
        // the returned decision is no longer awaiting approval
        assertThat(expect(HttpMethod.POST, base + "/decisions/" + decisionId + "/approval", meera, v,
                Map.of("action", "APPROVE"), HttpStatus.NOT_FOUND).get("code").asText()).isEqualTo("DECISION_NOT_FOUND");

        JsonNode audit = expect(HttpMethod.GET, base + "/audit", arun, null, null, HttpStatus.OK);
        assertThat(audit).extracting(e -> e.get("type").asText())
                .contains("DecisionProposed", "ClaimRejected", "ClaimReopened", "DecisionReturned");
    }

    @Test
    void paymentWebhookRejectsMissingAndForgedSignatures() throws Exception {
        String body = json.writeValueAsString(Map.of("instructionId", UUID.randomUUID().toString(), "status", "PAID"));

        HttpHeaders unsigned = new HttpHeaders();
        unsigned.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> missing = http.exchange(PaymentCallbackController.PATH, HttpMethod.POST,
                new HttpEntity<>(body, unsigned), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json.readTree(missing.getBody()).get("code").asText()).isEqualTo("INVALID_SIGNATURE");

        HttpHeaders forged = new HttpHeaders();
        forged.setContentType(MediaType.APPLICATION_JSON);
        forged.set("X-Signature", "sha256=" + PaymentCallbackController.sign("not-the-secret".getBytes(), body));
        ResponseEntity<String> wrongKey = http.exchange(PaymentCallbackController.PATH, HttpMethod.POST,
                new HttpEntity<>(body, forged), String.class);
        assertThat(wrongKey.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------ helpers

    private String submitAsCustomer(String token) throws Exception {
        HttpHeaders headers = bearer(token);
        headers.set(Headers.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        Map<String, Object> body = Map.of("policyNumber", "SG-MTR-POL-1001", "lineOfBusiness", "MOTOR",
                "incidentAt", "2026-11-10T09:00:00Z",
                "claimedAmount", Map.of("amount", "12000.00", "currency", "SGD"),
                "incident", Map.of("peril", "COLLISION", "description", "Side swiped in car park", "location", "Tampines",
                        "vehicleRegistration", "SBA1234X", "driverName", "Priya Nair", "damageDescription", "Driver door",
                        "thirdParties", List.of(Map.of("name", "Ahmad Ali", "vehicleRegistration", "SKL4321M"))));
        ResponseEntity<String> created = http.exchange("/claimant/v1/claims", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(created.getBody()).get("claimId").asText();
    }

    private String token(String username) {
        return http.postForEntity("/auth/login", Map.of("username", username, "password", "ChangeMe123!"), JsonNode.class)
                .getBody().get("access_token").asText();
    }

    private static Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "SGD");
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String token, String ifMatch, Object body) {
        HttpHeaders headers = bearer(token);
        if (ifMatch != null) {
            headers.setIfMatch("\"" + ifMatch + "\"");
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode expect(HttpMethod method, String path, String token, String ifMatch, Object body, HttpStatus expected)
            throws Exception {
        ResponseEntity<String> response = exchange(method, path, token, ifMatch, body);
        assertThat(response.getStatusCode()).as("%s %s -> %s", method, path, response.getBody()).isEqualTo(expected);
        return json.readTree(response.getBody());
    }

    /** Runs a staff POST command and returns the new version (from the ETag) for the next If-Match. */
    private String command(String path, String token, String ifMatch, Object body, HttpStatus expected) {
        ResponseEntity<String> response = exchange(HttpMethod.POST, path, token, ifMatch, body);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody()).isEqualTo(expected);
        return etag(response);
    }

    private static String etag(ResponseEntity<String> response) {
        return response.getHeaders().getETag().replace("\"", "");
    }

    private JsonNode customer(HttpMethod method, String path, String token, Object body, HttpStatus expected) throws Exception {
        ResponseEntity<String> response = http.exchange(path, method, new HttpEntity<>(body, bearer(token)), String.class);
        assertThat(response.getStatusCode()).as("%s %s -> %s", method, path, response.getBody()).isEqualTo(expected);
        return json.readTree(response.getBody());
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
