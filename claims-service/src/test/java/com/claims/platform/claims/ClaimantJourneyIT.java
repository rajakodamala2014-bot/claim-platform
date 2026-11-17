package com.claims.platform.claims;

import com.claims.platform.claims.core.repository.ClaimRepository;
import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The customer journey C1-C10 over real HTTP: dev token -> policies -> pre-signed upload -> FNOL (idempotent)
 * -> list/detail/timeline -> answer an information request -> withdraw / appeal rules. Real PostgreSQL 16;
 * the Policy system is WireMock loaded with the same mappings docker-compose uses (docker/policy-system).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ClaimantJourneyIT {
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
        Path storage = Files.createTempDirectory("claims-docs");
        registry.add("claims.documents.storage-dir", storage::toString);
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
    ClaimRepository claims;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    JdbcTemplate sql;

    @Test
    void customerReportsTracksAnswersAndWithdraws() throws Exception {
        String priya = token("priya");

        // C1 policies from the Policy system
        JsonNode policies = call(HttpMethod.GET, "/claimant/v1/policies", priya, null, HttpStatus.OK);
        assertThat(policies.at("/items")).extracting(p -> p.get("policyNumber").asText())
                .containsExactlyInAnyOrder("SG-MTR-POL-1001", "SG-PRP-POL-2001", "SG-MTR-POL-1500");

        // C2 pre-signed upload, PUT straight to storage
        UUID photo = upload(priya, "dashcam.jpg", new byte[2048]);

        // C3 FNOL, idempotent, cover confirmed inline
        UUID key = UUID.randomUUID();
        HttpHeaders fnolHeaders = auth(priya);
        fnolHeaders.set(Headers.IDEMPOTENCY_KEY, key.toString());
        fnolHeaders.set(Headers.CORRELATION_ID, "journey-corr-1");
        String fnol = fnol("SG-MTR-POL-1001", List.of(photo));
        ResponseEntity<String> created = http.exchange("/claimant/v1/claims", HttpMethod.POST,
                new HttpEntity<>(fnol, json(fnolHeaders)), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode claim = json.readTree(created.getBody());
        UUID claimId = UUID.fromString(claim.get("claimId").asText());
        assertThat(claim.get("claimNumber").asText()).matches("SG-MTR-\\d{4}-\\d{6}");
        assertThat(claim.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(claim.get("coverStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(created.getHeaders().getLocation()).hasToString("/claimant/v1/claims/" + claimId);

        ResponseEntity<String> replay = http.exchange("/claimant/v1/claims", HttpMethod.POST,
                new HttpEntity<>(fnol, json(fnolHeaders)), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(json.readTree(replay.getBody()).get("claimId").asText()).isEqualTo(claimId.toString());
        assertThat(sql.queryForObject("SELECT count(*) FROM core.claim WHERE claimant_id = 'cust-priya'", Integer.class)).isEqualTo(1);

        // events written to the outbox with the caller's correlation id, keyed by claim id
        assertThat(sql.queryForList("SELECT event_type FROM platform.outbox WHERE message_key = ? ORDER BY seq",
                String.class, claimId.toString())).containsExactly("ClaimSubmitted");
        assertThat(sql.queryForList("SELECT DISTINCT headers->>'X-Correlation-Id' FROM platform.outbox WHERE message_key = ?",
                String.class, claimId.toString())).containsExactly("journey-corr-1");

        // C4 list, C5 detail
        JsonNode list = call(HttpMethod.GET, "/claimant/v1/claims", priya, null, HttpStatus.OK);
        assertThat(list.at("/items/0/claimId").asText()).isEqualTo(claimId.toString());
        ResponseEntity<String> detail = http.exchange("/claimant/v1/claims/" + claimId, HttpMethod.GET,
                new HttpEntity<>(auth(priya)), String.class);
        assertThat(detail.getHeaders().getETag()).isEqualTo("\"1\"");
        JsonNode view = json.readTree(detail.getBody());
        assertThat(view.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(view.at("/documents/0/fileName").asText()).isEqualTo("dashcam.jpg");
        assertThat(detail.getBody()).doesNotContainIgnoringCase("reserve").doesNotContain("handlerId");

        // other customers can't see it
        call(HttpMethod.GET, "/claimant/v1/claims/" + claimId, token("tan"), null, HttpStatus.NOT_FOUND);

        // a handler asks for information (staff API arrives in Phase 4; drive the aggregate directly)
        UUID requestId = UUID.randomUUID();
        staff(claimId, c -> {
            Actor arun = Actor.handler("h-arun");
            c.assign("h-arun", "team-sg-motor", arun, Instant.now());
            c.startAssessment(arun, Instant.now());
            c.changeReserve(com.claims.platform.common.money.Money.of("5000", "SGD"), "Initial", arun, Instant.now());
            c.requestInformation(requestId, List.of("Police report"), Instant.now().plus(Duration.ofDays(14)), arun, Instant.now());
        });
        assertThat(call(HttpMethod.GET, "/claimant/v1/claims", priya, null, HttpStatus.OK).at("/items/0/actionNeeded").asBoolean()).isTrue();

        // C7 info requests, C8 respond with a document
        JsonNode requests = call(HttpMethod.GET, "/claimant/v1/claims/" + claimId + "/info-requests", priya, null, HttpStatus.OK);
        assertThat(requests.at("/items/0/status").asText()).isEqualTo("OPEN");
        assertThat(requests.at("/items/0/items/0").asText()).isEqualTo("Police report");
        UUID report = upload(priya, "police-report.pdf", "%PDF-1.4 test".getBytes());
        JsonNode answered = call(HttpMethod.POST, "/claimant/v1/claims/" + claimId + "/info-requests/" + requestId + "/responses",
                priya, Map.of("answer", "Report attached", "uploadIds", List.of(report)), HttpStatus.OK);
        assertThat(answered.get("status").asText()).isEqualTo("UNDER_ASSESSMENT");
        call(HttpMethod.POST, "/claimant/v1/claims/" + claimId + "/info-requests/" + requestId + "/responses",
                priya, Map.of("answer", "again"), HttpStatus.NOT_FOUND);

        // C6 timeline: customer-visible only, no reserves
        JsonNode timeline = call(HttpMethod.GET, "/claimant/v1/claims/" + claimId + "/timeline", priya, null, HttpStatus.OK);
        assertThat(timeline).extracting(e -> e.get("type").asText()).containsExactly("ClaimSubmitted",
                "ClaimAssigned", "AssessmentStarted", "InformationRequested", "InformationProvided");
        assertThat(timeline.toString()).doesNotContain("ReserveChanged").doesNotContain("newOutstanding");

        // C10 appeal only after a rejection; C9 withdraw before a decision, once
        JsonNode appeal = call(HttpMethod.POST, "/claimant/v1/claims/" + claimId + "/appeal", priya,
                Map.of("reason", "please"), HttpStatus.CONFLICT);
        assertThat(appeal.get("code").asText()).isEqualTo("CLAIM_INVALID_TRANSITION");
        assertThat(call(HttpMethod.POST, "/claimant/v1/claims/" + claimId + "/withdraw", priya,
                Map.of("reason", "Settled privately"), HttpStatus.OK).get("status").asText()).isEqualTo("WITHDRAWN");
        call(HttpMethod.POST, "/claimant/v1/claims/" + claimId + "/withdraw", priya, Map.of(), HttpStatus.CONFLICT);
        assertThat(sql.queryForObject("SELECT sum(amount_minor) FROM handling.reserve_movement WHERE claim_id = ?",
                Long.class, claimId)).as("reserve released on withdrawal").isZero();
    }

    @Test
    void slowPolicySystemNeverBlocksFnol() throws Exception {
        String priya = token("priya");
        HttpHeaders headers = auth(priya);
        headers.set(Headers.IDEMPOTENCY_KEY, UUID.randomUUID().toString());

        long started = System.nanoTime();
        ResponseEntity<String> response = http.exchange("/claimant/v1/claims", HttpMethod.POST,
                new HttpEntity<>(fnol("SG-MTR-POL-1500", List.of()), json(headers)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(body.get("coverStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void uploadUrlsAreSingleUseAndTamperProof() throws Exception {
        String priya = token("priya");
        JsonNode target = call(HttpMethod.POST, "/claimant/v1/uploads", priya,
                Map.of("fileName", "a.png", "contentType", "image/png", "sizeBytes", 10), HttpStatus.CREATED);
        String url = pathOf(target.get("uploadUrl").asText());

        assertThat(put(url.replaceAll("signature=[0-9a-f]+", "signature=deadbeef"), "image/png", new byte[10]).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(put(url, "image/jpeg", new byte[10]).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(put(url, "image/png", new byte[11]).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(put(url, "image/png", new byte[10]).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put(url, "image/png", new byte[10]).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------ helpers

    private String token(String username) {
        ResponseEntity<JsonNode> response = http.postForEntity("/auth/login",
                Map.of("username", username, "password", "ChangeMe123!"), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("access_token").asText();
    }

    private UUID upload(String token, String fileName, byte[] bytes) throws Exception {
        String type = fileName.endsWith(".pdf") ? "application/pdf" : "image/jpeg";
        JsonNode target = call(HttpMethod.POST, "/claimant/v1/uploads", token,
                Map.of("fileName", fileName, "contentType", type, "sizeBytes", bytes.length), HttpStatus.CREATED);
        assertThat(put(pathOf(target.get("uploadUrl").asText()), type, bytes).getStatusCode()).isEqualTo(HttpStatus.OK);
        return UUID.fromString(target.get("uploadId").asText());
    }

    private ResponseEntity<String> put(String path, String contentType, byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        return http.exchange(java.net.URI.create(http.getRootUri() + path), HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    private static String pathOf(String url) {
        java.net.URI uri = java.net.URI.create(url);
        return uri.getRawPath() + "?" + uri.getRawQuery();
    }

    private JsonNode call(HttpMethod method, String path, String token, Object body, HttpStatus expected) throws Exception {
        ResponseEntity<String> response = http.exchange(path, method, new HttpEntity<>(body, json(auth(token))), String.class);
        assertThat(response.getStatusCode()).as("%s %s -> %s", method, path, response.getBody()).isEqualTo(expected);
        return response.getBody() == null ? json.createObjectNode() : json.readTree(response.getBody());
    }

    private static HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static HttpHeaders json(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void staff(UUID claimId, java.util.function.Consumer<Claim> action) {
        tx.executeWithoutResult(s -> {
            Claim claim = claims.findById(new ClaimId(claimId)).orElseThrow();
            action.accept(claim);
            claims.save(claim);
        });
    }

    private String fnol(String policyNumber, List<UUID> uploads) throws Exception {
        return json.writeValueAsString(Map.of(
                "policyNumber", policyNumber,
                "lineOfBusiness", "MOTOR",
                "incidentAt", "2026-11-10T09:00:00Z",
                "incident", Map.of("peril", "THEFT", "description", "Car stolen from car park", "location", "Tampines",
                        "vehicleRegistration", "SBA1234X", "driverName", "Priya Nair", "damageDescription", "Vehicle missing"),
                "claimedAmount", Map.of("amount", "25000.00", "currency", "SGD"),
                "uploadIds", uploads,
                "contact", Map.of("phone", "+6591234567")));
    }
}
