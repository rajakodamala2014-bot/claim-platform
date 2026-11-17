package com.claims.platform.notification;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.common.messaging.Topics;
import com.claims.platform.notification.common.HmacSignature;
import com.claims.platform.notification.service.NotificationDispatchService;
import com.claims.platform.testsupport.TestKafka;
import com.claims.platform.testsupport.TestPostgres;
import com.claims.platform.web.config.PlatformSecurityAutoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Events on Kafka -> planned notifications (recipients, channels, language) -> dispatched through the fake
 * providers (retry, failure) -> N1-N7 over HTTP. PostgreSQL 16 + Kafka.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "notifications.dispatch.enabled=false")
@ActiveProfiles("dev")
class NotificationIT {
    private static final String JWT_SECRET = "local-dev-only-secret-change-me-0123456789abcdef";
    private static final String CALLBACK_SECRET = "local-dev-only-provider-callback-secret-0123456789";

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestPostgres.registerFreshDatabase(registry);
        TestKafka.register(registry, Topics.CLAIM_EVENTS, Topics.INSIGHTS_ALERTS, Topics.dlq(Topics.CLAIM_EVENTS),
                Topics.dlq(Topics.INSIGHTS_ALERTS));
    }

    @Autowired
    TestRestTemplate http;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate sql;
    @Autowired
    NotificationDispatchService dispatchService;
    @Autowired
    TransactionTemplate tx;

    @Test
    void customerAndStaffAreToldInTheirLanguageOnTheirChannels() throws Exception {
        UUID claim = UUID.randomUUID();
        send(claim, EventTypes.CLAIM_SUBMITTED, Map.of("claimNumber", "SG-MTR-2026-000301", "claimantId", "cust-priya"));
        send(claim, EventTypes.CLAIM_ASSIGNED, Map.of("handlerId", "h-arun", "teamId", "team-sg-motor"));
        send(claim, EventTypes.DECISION_PROPOSED, Map.of("decisionId", UUID.randomUUID().toString(), "outcome", "APPROVE",
                "amountMinor", 1_200_000, "currency", "SGD", "approvalRequired", true, "actorId", "h-arun"));
        send(claim, EventTypes.CLAIM_APPROVED, Map.of("approvedAmountMinor", 1_200_000, "currency", "SGD"));
        // Every adjuster on the team is told about the proposal, then the customer is told it is approved.
        await(() -> sql.queryForObject(
                "SELECT count(*) FROM notification.notification WHERE claim_id = ? AND event_type = ?",
                Integer.class, claim, EventTypes.CLAIM_APPROVED) >= 4);
        runDispatch();

        // N1 customer inbox, newest first, rendered
        String priya = token("cust-priya", "CUSTOMER", List.of("SG"));
        JsonNode inbox = get("/claimant/v1/notifications", priya, HttpStatus.OK);
        assertThat(inbox.at("/items")).extracting(i -> i.get("title").asText()).containsExactly(
                "Your claim SG-MTR-2026-000301 is approved", "Arun Kumar is now handling your claim",
                "We have received your claim SG-MTR-2026-000301");
        assertThat(inbox.at("/items/0/message").asText()).contains("SGD 12000.00");

        // team lead told about the decision waiting for approval
        JsonNode leadInbox = json.readTree(sql.queryForObject(
                "SELECT json_agg(body) FROM notification.notification WHERE recipient_id = 'tl-meera' AND channel = 'IN_APP'", String.class));
        assertThat(leadInbox.get(0).asText()).contains("Arun Kumar proposes to approve SG-MTR-2026-000301 for SGD 12000.00");

        // N2 read receipt (own messages only)
        String first = inbox.at("/items/0/id").asText();
        exchange(HttpMethod.POST, "/claimant/v1/notifications/" + first + "/read", priya, null, HttpStatus.NO_CONTENT);
        assertThat(get("/claimant/v1/notifications?unreadOnly=true", priya, HttpStatus.OK).at("/items")).hasSize(2);
        exchange(HttpMethod.POST, "/claimant/v1/notifications/" + first + "/read", token("cust-tan", "CUSTOMER", List.of("SG")),
                null, HttpStatus.NOT_FOUND);

        // N5 staff delivery log, market-scoped, addresses masked
        JsonNode log = get("/staff/v1/claims/" + claim + "/notifications", token("h-arun", "CLAIMS_ADJUSTER", List.of("SG")), HttpStatus.OK);
        assertThat(log.at("/items")).extracting(i -> i.get("channel").asText()).contains("EMAIL", "SMS", "PUSH", "IN_APP");
        assertThat(log.toString()).doesNotContain("priya@example.com").contains("p***@example.com");
        get("/staff/v1/claims/" + claim + "/notifications", token("h-my", "CLAIMS_ADJUSTER", List.of("MY")), HttpStatus.FORBIDDEN);
    }

    @Test
    void customersGetTheirMarketLanguage() throws Exception {
        UUID claim = UUID.randomUUID();
        send(claim, EventTypes.CLAIM_SUBMITTED, Map.of("claimNumber", "MY-PRP-2026-000302", "claimantId", "cust-aisyah"), "MY");
        awaitRows(claim, 3); // in-app, email, sms
        runDispatch();

        JsonNode inbox = get("/claimant/v1/notifications", token("cust-aisyah", "CUSTOMER", List.of("MY")), HttpStatus.OK);
        assertThat(inbox.at("/items/0/title").asText()).isEqualTo("Kami telah menerima tuntutan anda MY-PRP-2026-000302");
    }

    @Test
    void hongKongCustomersReadTraditionalChinese() throws Exception {
        UUID claim = UUID.randomUUID();
        send(claim, EventTypes.CLAIM_SUBMITTED, Map.of("claimNumber", "HK-MTR-2026-000401", "claimantId", "cust-wong"), "HK");
        awaitRows(claim, 3); // in-app, email, sms
        runDispatch();

        JsonNode inbox = get("/claimant/v1/notifications", token("cust-wong", "CUSTOMER", List.of("HK")), HttpStatus.OK);
        assertThat(inbox.at("/items/0/title").asText()).isEqualTo("我們已收到您的索償申請 HK-MTR-2026-000401");
    }

    @Test
    void providerFailuresAreRetriedThreeTimesThenLoggedFailedAndReceiptsAreSigned() throws Exception {
        sql.update("INSERT INTO notification.preference (user_id, language, phone, sms_enabled, email, email_enabled) "
                + "VALUES ('cust-flaky', 'en', '+6512340000', TRUE, 'flaky@example.com', TRUE)");
        UUID claim = UUID.randomUUID();
        send(claim, EventTypes.CLAIM_SUBMITTED, Map.of("claimNumber", "SG-MTR-2026-000303", "claimantId", "cust-flaky"));
        awaitRows(claim, 3);

        runDispatch();                       // attempt 1 (sms fails), in-app + email sent
        sql.update("UPDATE notification.notification SET next_attempt_at = now() - interval '1 minute' WHERE claim_id = ? AND status = 'PENDING'", claim);
        runDispatch();                       // attempt 2
        sql.update("UPDATE notification.notification SET next_attempt_at = now() - interval '1 minute' WHERE claim_id = ? AND status = 'PENDING'", claim);
        runDispatch();                       // attempt 3 -> FAILED

        Map<String, Object> sms = sql.queryForMap("SELECT status, attempts, last_error FROM notification.notification "
                + "WHERE claim_id = ? AND channel = 'SMS'", claim);
        assertThat(sms).containsEntry("status", "FAILED").containsEntry("attempts", 3);
        assertThat(sms.get("last_error").toString()).contains("simulated provider error");

        // N7: the e-mail provider confirms delivery (signed); unsigned receipts are rejected
        String messageId = sql.queryForObject("SELECT provider_message_id FROM notification.notification "
                + "WHERE claim_id = ? AND channel = 'EMAIL'", String.class, claim);
        String receipt = "{\"providerMessageId\":\"" + messageId + "\",\"status\":\"DELIVERED\"}";
        HttpHeaders unsigned = new HttpHeaders();
        unsigned.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.exchange("/internal/v1/providers/fake-email/delivery-status", HttpMethod.POST,
                new HttpEntity<>(receipt, unsigned), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders signed = new HttpHeaders();
        signed.setContentType(MediaType.APPLICATION_JSON);
        signed.set("X-Signature", "sha256=" + HmacSignature.sign(CALLBACK_SECRET.getBytes(StandardCharsets.UTF_8), receipt));
        ResponseEntity<String> delivered = http.exchange("/internal/v1/providers/fake-email/delivery-status", HttpMethod.POST,
                new HttpEntity<>(receipt, signed), String.class);
        assertThat(delivered.getStatusCode()).as(delivered.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(delivered.getBody()).get("status").asText()).isEqualTo("DELIVERED");
    }

    @Test
    void slaAlertsReachHandlerAndTeamLeadEvenBeforeTheClaimEventsArrive() throws Exception {
        UUID claim = UUID.randomUUID(); // no ClaimSubmitted seen: topics are not ordered relative to each other
        EventEnvelope alert = new EventEnvelope(UUID.randomUUID(), EventTypes.SLA_AT_RISK, 1, Instant.now(), claim, "SG", "MOTOR",
                null, Map.of("claimNumber", "SG-MTR-2026-000304", "handlerId", "h-kumar", "teamId", "team-sg-motor",
                "hoursRemaining", 3, "slaDueAt", "2026-11-16T02:00:00Z"));
        kafka.send(Topics.INSIGHTS_ALERTS, claim.toString(), json.writeValueAsString(alert)).get();
        kafka.send(Topics.INSIGHTS_ALERTS, claim.toString(), json.writeValueAsString(alert)).get(); // duplicate

        await(() -> sql.queryForObject("SELECT count(*) FROM notification.notification WHERE event_id = ?", Integer.class,
                alert.eventId()) == 6); // every adjuster on the team (arun, kumar, meera), in-app + e-mail each
        Thread.sleep(500);
        assertThat(sql.queryForList("SELECT DISTINCT recipient_id FROM notification.notification WHERE event_id = ? ORDER BY 1",
                String.class, alert.eventId())).containsExactly("h-arun", "h-kumar", "tl-meera");
        assertThat(sql.queryForObject("SELECT body FROM notification.notification WHERE event_id = ? AND recipient_id = 'h-kumar' "
                + "AND channel = 'IN_APP'", String.class, alert.eventId())).contains("breaches its SLA in 3 hours");
    }

    @Test
    void preferencesAndTemplatesAreManaged() throws Exception {
        String olivia = token("cust-olivia", "CUSTOMER", List.of("AU"));
        assertThat(get("/claimant/v1/notification-preferences", olivia, HttpStatus.OK).at("/sms/enabled").asBoolean()).isTrue();
        exchange(HttpMethod.PUT, "/claimant/v1/notification-preferences", olivia, Map.of("language", "en",
                "email", Map.of("enabled", true, "address", "not-an-email"), "sms", Map.of("enabled", false),
                "push", Map.of("enabled", false)), HttpStatus.BAD_REQUEST);
        JsonNode updated = json.readTree(exchange(HttpMethod.PUT, "/claimant/v1/notification-preferences", olivia, Map.of("language", "en",
                "email", Map.of("enabled", false), "sms", Map.of("enabled", false), "push", Map.of("enabled", true)), HttpStatus.OK).getBody());
        assertThat(updated.at("/push/enabled").asBoolean()).isTrue();

        String manager = token("m-lee", "CLAIMS_ADJUSTER", List.of("SG"));
        assertThat(get("/admin/v1/templates", manager, HttpStatus.OK).at("/items").size()).isGreaterThanOrEqualTo(43);
        get("/admin/v1/templates", token("cust-priya", "CUSTOMER", List.of("SG")), HttpStatus.FORBIDDEN);
        JsonNode template = json.readTree(exchange(HttpMethod.PUT, "/admin/v1/templates/ClaimRejected.CUSTOMER.th", manager,
                Map.of("subject", "Claim {{claimNumber}} received", "body", "Thanks, we are on it."), HttpStatus.OK).getBody());
        assertThat(template.get("subject").asText()).isEqualTo("Claim {{claimNumber}} received");
    }

    // ------------------------------------------------------------------ helpers

    private void send(UUID claim, String type, Map<String, Object> data) throws Exception {
        send(claim, type, data, "SG");
    }

    private void send(UUID claim, String type, Map<String, Object> data, String market) throws Exception {
        EventEnvelope event = new EventEnvelope(UUID.randomUUID(), type, 1, Instant.now(), claim, market, "MOTOR", null,
                new HashMap<>(data));
        kafka.send(Topics.CLAIM_EVENTS, claim.toString(), json.writeValueAsString(event)).get();
    }

    private void awaitRows(UUID claim, int expected) throws InterruptedException {
        await(() -> sql.queryForObject("SELECT count(*) FROM notification.notification WHERE claim_id = ?", Integer.class, claim) >= expected);
    }

    private void runDispatch() {
        tx.execute(s -> dispatchService.dispatchPendingNotifications());
    }

    private JsonNode get(String path, String token, HttpStatus expected) throws Exception {
        return json.readTree(exchange(HttpMethod.GET, path, token, null, expected).getBody());
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String token, Object body, HttpStatus expected) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).as("%s %s -> %s", method, path, response.getBody()).isEqualTo(expected);
        return response;
    }

    private static String token(String sub, String role, List<String> markets) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(PlatformSecurityAutoConfiguration.devKey(JWT_SECRET)));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("claims-dev-idp").subject(sub)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600))
                .claim("roles", List.of(role)).claim("markets", markets).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static void await(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline && !condition.get()) {
            Thread.sleep(200);
        }
        assertThat(condition.get()).as("condition met within 30s").isTrue();
    }
}
