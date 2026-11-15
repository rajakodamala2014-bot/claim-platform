package com.claims.platform.e2e;

import com.claims.platform.common.messaging.Topics;
import com.claims.platform.testsupport.TestKafka;
import com.claims.platform.testsupport.TestPostgres;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole platform, end to end, as separate processes (like production): a customer reports a claim
 * (Claims), handlers see it in their queue (Insights), the customer is told (Notifications), a handler assesses
 * and proposes above their limit, a team lead approves, payment goes out on Kafka to the Payments simulator and
 * comes back, the claim settles, exposure drops and the customer hears they were paid.
 */
class CrossServiceJourneyIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ServiceProcesses SERVICES = new ServiceProcesses();
    private static final WireMockServer POLICY_SYSTEM = new WireMockServer(WireMockConfiguration.options()
            .dynamicPort().usingFilesUnderDirectory("../docker/policy-system"));

    private static String claims;
    private static String insights;
    private static String notifications;

    @BeforeAll
    static void startPlatform() throws Exception {
        POLICY_SYSTEM.start();
        String kafka = TestKafka.bootstrapServers();
        TestKafka.createTopics(Topics.CLAIM_EVENTS, Topics.SETTLEMENT_REQUESTS, Topics.PAYMENT_RESULTS, Topics.INSIGHTS_ALERTS,
                Topics.dlq(Topics.CLAIM_EVENTS), Topics.dlq(Topics.PAYMENT_RESULTS), Topics.dlq(Topics.INSIGHTS_ALERTS));
        Map<String, String> common = Map.of("KAFKA_BOOTSTRAP", kafka, "DB_USER", "claims", "DB_PASSWORD", "claims");

        claims = SERVICES.start("claims-service", with(common, Map.of(
                "DB_URL", TestPostgres.createDatabase("e2e_claims_" + suffix()),
                "POLICY_BASE_URL", POLICY_SYSTEM.baseUrl(),
                "DOCUMENT_STORAGE_DIR", Files.createTempDirectory("e2e-docs").toString())));
        insights = SERVICES.start("insights-service", with(common, Map.of("DB_URL", TestPostgres.createDatabase("e2e_insights_" + suffix()))));
        notifications = SERVICES.start("notification-service",
                with(common, Map.of("DB_URL", TestPostgres.createDatabase("e2e_notification_" + suffix()))));
        String payments = SERVICES.start("payments-simulator", with(common, Map.of("PAYMENT_DELAY", "0s")));
        SERVICES.awaitHealthy(Duration.ofMinutes(3), claims, insights, notifications, payments);
    }

    @AfterAll
    static void stopPlatform() {
        SERVICES.close();
        POLICY_SYSTEM.stop();
    }

    @Test
    void claimTravelsFromFnolToSettlementAcrossAllServices() throws Exception {
        String priya = token("priya"), arun = token("arun"), meera = token("meera");

        // Stage 1 - customer reports the incident (Claims)
        JsonNode created = call("POST", claims + "/claimant/v1/claims", priya, Map.of(
                "policyNumber", "SG-MTR-POL-1001", "lineOfBusiness", "MOTOR", "incidentAt", "2026-11-10T09:00:00Z",
                "incident", Map.of("peril", "COLLISION", "description", "Rear-ended at lights", "location", "Orchard Rd",
                        "vehicleRegistration", "SBA1234X", "driverName", "Priya Nair", "damageDescription", "Rear bumper",
                        "thirdParties", List.of(Map.of("name", "John Lim")))),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()), 201);
        String claimId = created.get("claimId").asText();
        String claimNumber = created.get("claimNumber").asText();
        assertThat(created.get("status").asText()).isEqualTo("TRIAGED");

        // ... the handlers' queue (Insights) and the customer's inbox (Notifications) catch up asynchronously
        eventually(() -> call("GET", insights + "/staff/v1/work-queue?view=unassigned", arun, null, Map.of(), 200),
                q -> q.toString().contains(claimNumber));
        eventually(() -> call("GET", notifications + "/claimant/v1/notifications", priya, null, Map.of(), 200),
                inbox -> inbox.toString().contains("We have received your claim " + claimNumber));

        // Stage 2-4 - pick up, assess, propose above the handler's SGD 10,000 limit (Claims)
        String base = claims + "/staff/v1/claims/" + claimId;
        long v = call("GET", base, arun, null, Map.of(), 200).at("/claim/version").asLong();
        v = command(base + "/assignment", arun, v, null, 200);
        v = command(base + "/start-assessment", arun, v, null, 200);
        v = command(base + "/reserves", arun, v, Map.of("amount", money("12000.00"), "reason", "Repair quote"), 200);
        JsonNode proposed = call("POST", base + "/decisions", arun, Map.of("outcome", "APPROVE", "amount", money("12000.00")),
                Map.of("If-Match", "\"" + v + "\""), 202);
        String decisionId = proposed.at("/result/decisionId").asText();
        v = proposed.get("version").asLong();

        // the team lead sees it in the approvals inbox (Insights) and approves (Claims)
        eventually(() -> call("GET", insights + "/staff/v1/approvals", meera, null, Map.of(), 200),
                inbox -> inbox.toString().contains(decisionId));
        JsonNode approved = call("POST", base + "/decisions/" + decisionId + "/approval", meera, Map.of("action", "APPROVE"),
                Map.of("If-Match", "\"" + v + "\""), 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        v = approved.get("version").asLong();
        eventually(() -> call("GET", notifications + "/claimant/v1/notifications", priya, null, Map.of(), 200),
                inbox -> inbox.toString().contains("Your claim " + claimNumber + " is approved"));

        // Stage 5 - payment out to the Payments simulator over Kafka, result back over Kafka (Claims)
        command(base + "/settlements", arun, v, Map.of("payee", Map.of("name", "Priya Nair", "bankCode", "DBSSSGSG",
                "accountNumber", "123456789"), "amount", money("12000.00")), 201);
        eventually(() -> call("GET", base, arun, null, Map.of(), 200), ws -> "SETTLED".equals(ws.at("/claim/status").asText()));

        // ... customer is told they were paid, exposure drops on the manager's dashboard
        eventually(() -> call("GET", notifications + "/claimant/v1/notifications", priya, null, Map.of(), 200),
                inbox -> inbox.toString().contains("SGD 12000.00 has been paid to your account"));
        eventually(() -> call("GET", insights + "/staff/v1/search?q=" + claimNumber, meera, null, Map.of(), 200),
                found -> "SETTLED".equals(found.at("/items/0/status").asText()));
        JsonNode exposure = call("GET", insights + "/staff/v1/insights/exposure?market=SG&lob=MOTOR", meera, null, Map.of(), 200);
        assertThat(exposure.toString()).doesNotContain(claimNumber);
        JsonNode timeline = call("GET", claims + "/claimant/v1/claims/" + claimId + "/timeline", priya, null, Map.of(), 200);
        assertThat(timeline.findValuesAsText("type")).contains("ClaimSubmitted", "ClaimApproved", "PaymentConfirmed", "ClaimSettled")
                .doesNotContain("ReserveChanged", "DecisionProposed");
    }

    // ------------------------------------------------------------------ helpers

    private static String token(String username) throws Exception {
        return call("POST", claims + "/dev/token", null, Map.of("username", username), Map.of(), 200).get("access_token").asText();
    }

    private static long command(String url, String token, long version, Object body, int expected) throws Exception {
        return call("POST", url, token, body, Map.of("If-Match", "\"" + version + "\""), expected).get("version").asLong();
    }

    private static Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "SGD");
    }

    private static JsonNode call(String method, String url, String token, Object body, Map<String, String> headers, int expected)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        headers.forEach(request::header);
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s -> %s", method, url, response.body()).isEqualTo(expected);
        return response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }

    interface Call {
        JsonNode get() throws Exception;
    }

    /** Polls an eventually consistent read until it satisfies {@code condition} (60 s budget). */
    private static void eventually(Call call, Predicate<JsonNode> condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            last = call.get();
            if (condition.test(last)) {
                return;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("Not reached within 60s; last response: " + last);
    }

    private static Map<String, String> with(Map<String, String> a, Map<String, String> b) {
        Map<String, String> merged = new HashMap<>(a);
        merged.putAll(b);
        return merged;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
