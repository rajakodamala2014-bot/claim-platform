package com.claims.platform.claims;

import com.claims.platform.claims.settlement.controller.PaymentCallbackController;
import com.claims.platform.common.web.Headers;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stages 2-5 of the claim journey through the staff API, over real HTTP and PostgreSQL 16: pick-up race,
 * If-Match/412, information request round trip, assessment, a decision above the handler's limit approved by a
 * team lead, payment failure via the signed webhook, retry with corrected details, settlement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class StaffJourneyIT {
    private static final String WEBHOOK_SECRET = "local-dev-only-payments-webhook-secret-0123456789";
    private static final WireMockServer POLICY_SYSTEM = new WireMockServer(WireMockConfiguration.options()
            .dynamicPort().usingFilesUnderDirectory("../deployment/docker/policy-system"));

    static {
        POLICY_SYSTEM.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        TestPostgres.registerFreshDatabase(registry);
        com.claims.platform.testsupport.TestKafka.register(registry, ClaimsTopics.ALL);
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
    @Autowired
    JdbcTemplate sql;

    @Test
    void handlerAssessesTeamLeadApprovesPaymentFailsIsRetriedAndSettles() throws Exception {
        String priya = token("priya"), arun = token("arun"), kumar = token("kumar"), meera = token("meera");
        String claim = submitAsCustomer(priya);
        String base = "/staff/v1/claims/" + claim;

        // S2 workspace + ETag
        ResponseEntity<String> workspace = exchange(HttpMethod.GET, base, arun, null, null);
        assertThat(workspace.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(workspace.getHeaders().getETag()).isEqualTo("\"1\"");
        JsonNode ws = json.readTree(workspace.getBody());
        assertThat(ws.at("/claim/status").asText()).isEqualTo("TRIAGED");
        assertThat(ws.at("/policy/policyNumber").asText()).isEqualTo("SG-MTR-POL-1001");
        assertThat(ws.at("/incident/vehicleRegistration").asText()).isEqualTo("SBA1234X");
        assertThat(ws.at("/parties/0/role").asText()).isEqualTo("CLAIMANT");

        // S4 pick-up: first wins, second gets 409; If-Match is mandatory
        expect(HttpMethod.POST, base + "/assignment", kumar, null, null, HttpStatus.PRECONDITION_REQUIRED);
        String v = command(HttpMethod.POST, base + "/assignment", arun, "1", null, HttpStatus.OK);
        expect(HttpMethod.POST, base + "/assignment", kumar, v, null, HttpStatus.CONFLICT);

        // S5 with a stale version -> 412, then the right one
        expect(HttpMethod.POST, base + "/start-assessment", arun, "1", null, HttpStatus.PRECONDITION_FAILED);
        v = command(HttpMethod.POST, base + "/start-assessment", arun, v, null, HttpStatus.OK);

        // S6 reserve, S7 info request, customer answers (C8)
        v = command(HttpMethod.POST, base + "/reserves", arun, v,
                Map.of("amount", money("5000.00"), "reason", "Initial estimate"), HttpStatus.OK);
        JsonNode info = expect(HttpMethod.POST, base + "/info-requests", arun, v,
                Map.of("items", List.of("Police report", "Repair quote")), HttpStatus.CREATED);
        String requestId = info.get("result").asText();
        assertThat(customer(HttpMethod.POST, "/claimant/v1/claims/" + claim + "/info-requests/" + requestId + "/responses",
                priya, Map.of("answer", "Both attached")).get("status").asText()).isEqualTo("UNDER_ASSESSMENT");
        v = etag(exchange(HttpMethod.GET, base, arun, null, null));

        // S8 assessment, reserve adjusted
        v = command(HttpMethod.POST, base + "/assessments", arun, v,
                Map.of("liability", "FULL", "assessedAmount", money("12000.00"), "notes", "Third party at fault"), HttpStatus.CREATED);
        v = command(HttpMethod.POST, base + "/reserves", arun, v,
                Map.of("amount", money("12000.00"), "reason", "Assessed"), HttpStatus.OK);

        // S9 above the handler's SGD 10,000 limit -> 202, waits for a team lead
        JsonNode proposed = expect(HttpMethod.POST, base + "/decisions", arun, v,
                Map.of("outcome", "APPROVE", "amount", money("12000.00")), HttpStatus.ACCEPTED);
        assertThat(proposed.get("status").asText()).isEqualTo("DECISION_PENDING");
        assertThat(proposed.at("/result/approvalRequired").asBoolean()).isTrue();
        String decisionId = proposed.at("/result/decisionId").asText();
        v = String.valueOf(proposed.get("version").asLong());

        // S10 the proposer can't approve; the team lead can
        expect(HttpMethod.POST, base + "/decisions/" + decisionId + "/approval", arun, v,
                Map.of("action", "APPROVE"), HttpStatus.FORBIDDEN);
        JsonNode approved = expect(HttpMethod.POST, base + "/decisions/" + decisionId + "/approval", meera, v,
                Map.of("action", "APPROVE"), HttpStatus.OK);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        v = String.valueOf(approved.get("version").asLong());

        // staff outside the market can't even read it
        String myHandler = token("faiz");
        assertThat(expect(HttpMethod.GET, base, myHandler, null, null, HttpStatus.FORBIDDEN).get("code").asText())
                .isEqualTo("FORBIDDEN_MARKET");

        // S11 settlement: more than approved -> 422; to a closed account (simulated failure)
        expect(HttpMethod.POST, base + "/settlements", arun, v,
                Map.of("payee", payee("12340000"), "amount", money("12000.01")), HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode instructed = expect(HttpMethod.POST, base + "/settlements", arun, v,
                Map.of("payee", payee("12340000"), "amount", money("12000.00")), HttpStatus.CREATED);
        assertThat(instructed.get("status").asText()).isEqualTo("SETTLEMENT_IN_PROGRESS");
        String settlementId = instructed.at("/result/settlementId").asText();
        String firstInstruction = instructed.at("/result/instructionId").asText();
        // the instruction carries the account for Payments (masked by the relay once sent); at rest it is encrypted
        assertThat(sql.queryForObject("SELECT payload->>'accountNumber' FROM platform.outbox "
                + "WHERE topic = 'payments.settlement-requests' AND id = ?::uuid", String.class, firstInstruction)).isIn("12340000", "****0000");
        assertThat(sql.queryForObject("SELECT account_number_enc FROM settlement.settlement WHERE id = ?::uuid", String.class, settlementId))
                .startsWith("v1:").doesNotContain("12340000");

        // Payments reports FAILED through the signed webhook -> handler fixes the details and retries (S12)
        assertThat(webhook(claim, settlementId, firstInstruction, "FAILED")).isEqualTo("APPLIED");
        JsonNode afterFailure = json.readTree(exchange(HttpMethod.GET, base, arun, null, null).getBody());
        assertThat(afterFailure.at("/claim/settlement/status").asText()).isEqualTo("FAILED");
        assertThat(afterFailure.at("/claim/settlement/accountNumberMasked").asText()).isEqualTo("****0000");
        v = String.valueOf(afterFailure.at("/claim/version").asLong());
        JsonNode retried = expect(HttpMethod.POST, base + "/settlements/" + settlementId + "/retry", arun, v,
                Map.of("payee", payee("987654321")), HttpStatus.OK);
        String secondInstruction = retried.at("/result/instructionId").asText();
        assertThat(retried.at("/result/attempt").asInt()).isEqualTo(2);

        // a late result for the old attempt is ignored; the new one settles the claim
        assertThat(webhook(claim, settlementId, firstInstruction, "PAID")).isEqualTo("IGNORED_DUPLICATE");
        assertThat(webhook(claim, settlementId, secondInstruction, "PAID")).isEqualTo("APPLIED");
        assertThat(webhook(claim, settlementId, secondInstruction, "PAID")).isEqualTo("IGNORED_DUPLICATE");

        JsonNode settled = json.readTree(exchange(HttpMethod.GET, base, arun, null, null).getBody());
        assertThat(settled.at("/claim/status").asText()).isEqualTo("SETTLED");
        assertThat(settled.at("/claim/reserve/outstanding/amountMinor").asLong()).isZero();
        assertThat(settled.at("/claim/decision/decidedBy").asText()).isEqualTo("tl-meera");

        // S3 full audit vs the customer's view
        JsonNode audit = expect(HttpMethod.GET, base + "/audit", arun, null, null, HttpStatus.OK);
        assertThat(audit).extracting(e -> e.get("type").asText()).contains("DecisionProposed", "DecisionApproved",
                "ReserveChanged", "PaymentFailed", "PaymentConfirmed", "ClaimSettled");
        JsonNode timeline = customer(HttpMethod.GET, "/claimant/v1/claims/" + claim + "/timeline", priya, null);
        assertThat(timeline.toString()).doesNotContain("DecisionProposed").doesNotContain("PaymentFailed")
                .doesNotContain("ReserveChanged");
        JsonNode customerView = customer(HttpMethod.GET, "/claimant/v1/claims/" + claim, priya, null);
        assertThat(customerView.at("/decision/approvedAmount/amount").asText()).isEqualTo("12000.00");
        assertThat(customerView.at("/payment/status").asText()).isEqualTo("PAID");
    }

    @Test
    void staffRegisterClaimsOnTheCustomersBehalfWithinTheirMarkets() throws Exception {
        HttpHeaders headers = bearer(token("arun"));
        headers.set(Headers.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        Map<String, Object> body = Map.of("claimantId", "cust-priya", "claimantName", "Priya Nair",
                "policyNumber", "SG-PRP-POL-2001", "lineOfBusiness", "PROPERTY", "incidentAt", "2026-11-12T22:00:00Z",
                "incident", Map.of("peril", "WATER_DAMAGE", "description", "Pipe burst in kitchen",
                        "propertyAddress", "10 Marina Boulevard", "damagedItems", List.of(Map.of("description", "Cabinets"))));

        ResponseEntity<String> created = http.exchange("/staff/v1/claims", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode result = json.readTree(created.getBody());
        assertThat(result.get("claimNumber").asText()).startsWith("SG-PRP-");
        assertThat(sql.queryForObject("SELECT channel FROM core.claim WHERE id = ?::uuid", String.class,
                result.get("claimId").asText())).isEqualTo("STAFF");

        HttpHeaders myHeaders = bearer(token("faiz"));
        myHeaders.set(Headers.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        ResponseEntity<String> foreign = http.exchange("/staff/v1/claims", HttpMethod.POST, new HttpEntity<>(body, myHeaders), String.class);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(foreign.getBody()).contains("FORBIDDEN_MARKET");
    }

    // ------------------------------------------------------------------ helpers

    private String submitAsCustomer(String token) throws Exception {
        HttpHeaders headers = bearer(token);
        headers.set(Headers.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        Map<String, Object> body = Map.of("policyNumber", "SG-MTR-POL-1001", "lineOfBusiness", "MOTOR",
                "incidentAt", "2026-11-10T09:00:00Z",
                "incident", Map.of("peril", "COLLISION", "description", "Rear-ended at lights", "location", "Orchard Rd",
                        "vehicleRegistration", "SBA1234X", "driverName", "Priya Nair", "damageDescription", "Rear bumper",
                        "thirdParties", List.of(Map.of("name", "John Lim", "vehicleRegistration", "SGX9876Z"))));
        ResponseEntity<String> created = http.exchange("/claimant/v1/claims", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(created.getBody()).get("claimId").asText();
    }

    private String webhook(String claimId, String settlementId, String instructionId, String status) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instructionId", instructionId);
        result.put("settlementId", settlementId);
        result.put("claimId", claimId);
        result.put("status", status);
        result.put("paymentRef", "PAID".equals(status) ? "PAY-" + instructionId.substring(0, 8) : null);
        result.put("failureReason", "FAILED".equals(status) ? "ACCOUNT_CLOSED" : null);
        result.put("amountMinor", 1_200_000);
        result.put("currency", "SGD");
        result.put("processedAt", Instant.now().toString());
        String body = json.writeValueAsString(result);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Signature", "sha256=" + PaymentCallbackController.sign(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), body));
        ResponseEntity<String> response = http.exchange(PaymentCallbackController.PATH, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return json.readTree(response.getBody()).get("outcome").asText();
    }

    private String token(String username) {
        return http.postForEntity("/auth/login", Map.of("username", username, "password", "ChangeMe123!"), JsonNode.class)
                .getBody().get("access_token").asText();
    }

    private static Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "SGD");
    }

    private static Map<String, String> payee(String account) {
        return Map.of("name", "Priya Nair", "bankCode", "DBSSSGSG", "accountNumber", account);
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

    /** Runs a staff command and returns the new version (from the ETag) for the next If-Match. */
    private String command(HttpMethod method, String path, String token, String ifMatch, Object body, HttpStatus expected)
            throws Exception {
        ResponseEntity<String> response = exchange(method, path, token, ifMatch, body);
        assertThat(response.getStatusCode()).as("%s %s -> %s", method, path, response.getBody()).isEqualTo(expected);
        return etag(response);
    }

    private static String etag(ResponseEntity<String> response) {
        return response.getHeaders().getETag().replace("\"", "");
    }

    private JsonNode customer(HttpMethod method, String path, String token, Object body) throws Exception {
        ResponseEntity<String> response = http.exchange(path, method, new HttpEntity<>(body, bearer(token)), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("%s %s -> %s", method, path, response.getBody()).isTrue();
        return json.readTree(response.getBody());
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
