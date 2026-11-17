package com.claims.platform.insights;

import com.claims.platform.common.event.EventEnvelope;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.common.messaging.Topics;
import com.claims.platform.insights.service.SlaAlertService;
import com.claims.platform.insights.service.SlaAlertService.SlaAlertResult;
import com.claims.platform.testsupport.TestKafka;
import com.claims.platform.testsupport.TestPostgres;
import com.claims.platform.web.config.PlatformSecurityAutoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Insights end to end inside the service: claims events on Kafka -> read model in PostgreSQL 16 -> I1-I7 over
 * HTTP with real tokens, SLA alerts on insights.alerts, idempotency and the DLQ.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "insights.sla.alerts-enabled=false")
@ActiveProfiles("dev")
class InsightsIT {
    private static final String SECRET = "local-dev-only-secret-change-me-0123456789abcdef";

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestPostgres.registerFreshDatabase(registry);
        TestKafka.register(registry, Topics.CLAIM_EVENTS, Topics.dlq(Topics.CLAIM_EVENTS), Topics.INSIGHTS_ALERTS);
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
    SlaAlertService slaAlertService;

    @Test
    void readModelsFollowTheClaimsAndServeEveryStaffScreen() throws Exception {
        Instant now = Instant.now();
        // A: new, unassigned, SLA breached 1h ago        B: Arun's, under assessment, reserve SGD 5,000
        // C: Arun's, decision of SGD 12,000 awaiting a team lead       D: Kumar's, closed (for performance)
        // E: MY market (outside a SG-only viewer's scope)
        UUID a = claim("SG-MTR-2026-000101", "SG", "MOTOR", now.minus(Duration.ofHours(49)), 48);
        UUID b = claim("SG-MTR-2026-000102", "SG", "MOTOR", now.minus(Duration.ofHours(2)), 48);
        UUID c = claim("SG-PRP-2026-000103", "SG", "PROPERTY", now.minus(Duration.ofHours(10)), 48);
        UUID d = claim("SG-MTR-2026-000104", "SG", "MOTOR", now.minus(Duration.ofDays(5)), 48);
        UUID e = claim("MY-MTR-2026-000105", "MY", "MOTOR", now.minus(Duration.ofHours(1)), 48);

        send(b, EventTypes.CLAIM_TRIAGED, Map.of("coverStatus", "CONFIRMED"));
        send(b, EventTypes.CLAIM_ASSIGNED, Map.of("handlerId", "h-arun", "teamId", "team-sg-motor"));
        send(b, EventTypes.ASSESSMENT_STARTED, Map.of());
        send(b, EventTypes.RESERVE_CHANGED, Map.of("newOutstandingMinor", 500_000, "currency", "SGD"));
        send(c, EventTypes.CLAIM_ASSIGNED, Map.of("handlerId", "h-arun", "teamId", "team-sg-motor"));
        send(c, EventTypes.RESERVE_CHANGED, Map.of("newOutstandingMinor", 1_200_000, "currency", "SGD"));
        UUID decision = UUID.randomUUID();
        send(c, EventTypes.DECISION_PROPOSED, Map.of("decisionId", decision.toString(), "outcome", "APPROVE",
                "amountMinor", 1_200_000, "approvalRequired", true, "actorId", "h-arun"));
        send(d, EventTypes.CLAIM_ASSIGNED, Map.of("handlerId", "h-kumar", "teamId", "team-sg-motor"));
        send(d, EventTypes.CLAIM_APPROVED, Map.of());
        send(d, EventTypes.CLAIM_SETTLED, Map.of());
        send(d, EventTypes.CLAIM_CLOSED, Map.of());
        send(e, EventTypes.CLAIM_TRIAGED, Map.of("coverStatus", "CONFIRMED"));
        UUID paid = claim("SG-MTR-2026-000106", "SG", "MOTOR", now.minus(Duration.ofHours(6)), 48);
        UUID rejected = claim("SG-MTR-2026-000107", "SG", "MOTOR", now.minus(Duration.ofHours(7)), 48);
        send(paid, EventTypes.CLAIM_ASSIGNED, Map.of("handlerId", "h-arun", "teamId", "team-sg-motor"));
        send(paid, EventTypes.CLAIM_APPROVED, Map.of());
        send(paid, EventTypes.CLAIM_SETTLED, Map.of());
        send(rejected, EventTypes.CLAIM_ASSIGNED, Map.of("handlerId", "h-arun", "teamId", "team-sg-motor"));
        send(rejected, EventTypes.CLAIM_REJECTED, Map.of("reasonCode", "NOT_COVERED"));
        await(() -> count("status = 'CLOSED'") == 1 && count("pending_decision_id IS NOT NULL") == 1 && count("1=1") == 7);

        String arun = token("h-arun", "CLAIMS_ADJUSTER", List.of("SG"), "team-sg-motor");
        String meera = token("tl-meera", "CLAIMS_ADJUSTER", List.of("SG"), "team-sg-motor");
        String lee = token("m-lee", "CLAIMS_ADJUSTER", List.of("SG", "MY"), null);

        // I1 work queue: unassigned (SG only, oldest SLA first) and mine
        JsonNode unassigned = get("/staff/v1/work-queue?view=unassigned", arun, HttpStatus.OK);
        assertThat(unassigned.at("/items")).extracting(i -> i.get("claimNumber").asText()).containsExactly("SG-MTR-2026-000101");
        assertThat(unassigned.at("/items/0/slaState").asText()).isEqualTo("BREACHED");
        JsonNode mine = get("/staff/v1/work-queue?view=mine", arun, HttpStatus.OK);
        assertThat(mine.at("/items")).extracting(i -> i.get("claimNumber").asText())
                .containsExactly("SG-PRP-2026-000103", "SG-MTR-2026-000102"); // C due earlier than B
        JsonNode firstPage = get("/staff/v1/work-queue?view=mine&limit=1", arun, HttpStatus.OK);
        JsonNode secondPage = get("/staff/v1/work-queue?view=mine&limit=1&cursor=" + firstPage.get("nextCursor").asText(), arun, HttpStatus.OK);
        assertThat(secondPage.at("/items/0/claimNumber").asText()).isEqualTo("SG-MTR-2026-000102");
        assertThat(get("/staff/v1/work-queue?view=unassigned&market=MY", arun, HttpStatus.FORBIDDEN).get("code").asText())
                .isEqualTo("FORBIDDEN_MARKET");
        String kumar = token("h-kumar", "CLAIMS_ADJUSTER", List.of("SG"), "team-sg-motor");
        assertThat(get("/staff/v1/work-queue?view=paid", arun, HttpStatus.OK).at("/items"))
                .extracting(i -> i.get("claimNumber").asText()).containsExactly("SG-MTR-2026-000106");
        assertThat(get("/staff/v1/work-queue?view=rejected", arun, HttpStatus.OK).at("/items"))
                .extracting(i -> i.get("claimNumber").asText()).containsExactly("SG-MTR-2026-000107");
        assertThat(get("/staff/v1/work-queue?view=paid", kumar, HttpStatus.OK).at("/items")).isEmpty();
        assertThat(get("/staff/v1/work-queue?view=rejected", kumar, HttpStatus.OK).at("/items")).isEmpty();

        // I2 search
        assertThat(get("/staff/v1/search?q=000102", arun, HttpStatus.OK).at("/items/0/claimId").asText()).isEqualTo(b.toString());
        assertThat(get("/staff/v1/search?q=MY-MTR", arun, HttpStatus.OK).at("/items")).isEmpty();
        assertThat(get("/staff/v1/search?q=MY-MTR", lee, HttpStatus.OK).at("/items")).hasSize(1);

        // I3 approvals: any claims adjuster can open the inbox
        JsonNode inbox = get("/staff/v1/approvals", meera, HttpStatus.OK);
        assertThat(inbox.at("/items/0/decisionId").asText()).isEqualTo(decision.toString());
        assertThat(inbox.at("/items/0/amount/amount").asText()).isEqualTo("12000.00");
        get("/staff/v1/approvals", arun, HttpStatus.OK);

        // I4 workload, I5 performance
        JsonNode workload = get("/staff/v1/insights/workload", meera, HttpStatus.OK);
        assertThat(workload.at("/items/0/handlerId").asText()).isEqualTo("h-arun");
        assertThat(workload.at("/items/0/openClaims").asInt()).isEqualTo(2);
        JsonNode performance = get("/staff/v1/insights/performance", meera, HttpStatus.OK);
        assertThat(performance.at("/items/0/handlerId").asText()).isEqualTo("h-kumar");
        assertThat(performance.at("/items/0/closedClaims").asInt()).isEqualTo(1);
        get("/staff/v1/insights/workload?team=team-other", meera, HttpStatus.OK);

        // I6 SLA: A breached
        JsonNode sla = get("/staff/v1/insights/sla", meera, HttpStatus.OK);
        assertThat(sla.at("/items")).extracting(i -> i.get("claimNumber").asText()).contains("SG-MTR-2026-000101");
        assertThat(sla.at("/items/0/slaState").asText()).isEqualTo("BREACHED");

        // I7 exposure: SG motor 5,000 + SG property 12,000 (closed D excluded)
        JsonNode exposure = get("/staff/v1/insights/exposure?market=SG", meera, HttpStatus.OK);
        assertThat(exposure.at("/items")).hasSize(2);
        assertThat(findExposure(exposure, "MOTOR").at("/totalReserve/amount").asText()).isEqualTo("5000.00");
        assertThat(findExposure(exposure, "MOTOR").get("outstandingClaims").asInt()).isEqualTo(2);
        assertThat(findExposure(exposure, "PROPERTY").at("/totalReserve/amount").asText()).isEqualTo("12000.00");

        // SLA alert: A breached -> one SlaBreached on insights.alerts, not repeated on the next run
        try (KafkaConsumer<String, String> alerts = TestKafka.consumer(Topics.INSIGHTS_ALERTS)) {
            SlaAlertResult first = slaAlertService.publishDueSlaAlerts();
            SlaAlertResult second = slaAlertService.publishDueSlaAlerts();
            assertThat(first.breached()).isGreaterThanOrEqualTo(1);
            assertThat(second.breached()).isZero();
            List<ConsumerRecord<String, String>> received = TestKafka.poll(alerts, r -> a.toString().equals(r.key()), 1, Duration.ofSeconds(20));
            EventEnvelope alert = json.readValue(received.getFirst().value(), EventEnvelope.class);
            assertThat(alert.type()).isEqualTo(EventTypes.SLA_BREACHED);
            assertThat(alert.data()).containsEntry("claimNumber", "SG-MTR-2026-000101");
        }
    }

    @Test
    void duplicatesAreIgnoredAndGarbageIsDeadLettered() throws Exception {
        UUID claim = claim("SG-MTR-2026-000201", "SG", "MOTOR", Instant.now(), 48);
        EventEnvelope reserve = envelope(claim, EventTypes.RESERVE_CHANGED, Map.of("newOutstandingMinor", 300_000, "currency", "SGD"));
        kafka.send(Topics.CLAIM_EVENTS, claim.toString(), json.writeValueAsString(reserve)).get();
        kafka.send(Topics.CLAIM_EVENTS, claim.toString(), json.writeValueAsString(reserve)).get();
        await(() -> count("claim_id = '" + claim + "' AND reserve_outstanding_minor = 300000") == 1);
        assertThat(sql.queryForObject("SELECT count(*) FROM insights.processed_event WHERE message_id = ?", Integer.class,
                reserve.eventId().toString())).isEqualTo(1);

        String key = "garbage-" + UUID.randomUUID();
        try (KafkaConsumer<String, String> dlq = TestKafka.consumer(Topics.dlq(Topics.CLAIM_EVENTS))) {
            kafka.send(Topics.CLAIM_EVENTS, key, "not an event").get();
            assertThat(TestKafka.poll(dlq, r -> key.equals(r.key()), 1, Duration.ofSeconds(30))).isNotEmpty();
        }
    }

    @Test
    void customersAndAnonymousCallersAreRejected() {
        assertThat(http.getForEntity("/staff/v1/work-queue", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token("cust-priya", "CUSTOMER", List.of("SG"), null));
        assertThat(http.exchange("/staff/v1/work-queue", HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------ helpers

    private UUID claim(String number, String market, String lob, Instant submittedAt, int slaHours) throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> data = new HashMap<>(Map.of("claimNumber", number, "policyNumber", market + "-POL-" + number.substring(14),
                "claimantId", "cust-x", "channel", "APP", "currency", "MY".equals(market) ? "MYR" : "SGD",
                "slaDueAt", submittedAt.plus(Duration.ofHours(slaHours)).toString()));
        EventEnvelope submitted = new EventEnvelope(UUID.randomUUID(), EventTypes.CLAIM_SUBMITTED, 1, submittedAt, id, market, lob,
                null, data);
        kafka.send(Topics.CLAIM_EVENTS, id.toString(), json.writeValueAsString(submitted)).get();
        return id;
    }

    private void send(UUID claimId, String type, Map<String, Object> data) throws Exception {
        kafka.send(Topics.CLAIM_EVENTS, claimId.toString(), json.writeValueAsString(envelope(claimId, type, data))).get();
    }

    private EventEnvelope envelope(UUID claimId, String type, Map<String, Object> data) {
        String market = sql.queryForList("SELECT market_code FROM insights.claim_summary WHERE claim_id = ?", String.class, claimId)
                .stream().findFirst().map(String::trim).orElse("SG");
        return new EventEnvelope(UUID.randomUUID(), type, 1, Instant.now(), claimId, market, "MOTOR", null, data);
    }

    private int count(String where) {
        return sql.queryForObject("SELECT count(*) FROM insights.claim_summary WHERE " + where, Integer.class);
    }

    private JsonNode get(String path, String token, HttpStatus expected) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).as("%s -> %s", path, response.getBody()).isEqualTo(expected);
        return json.readTree(response.getBody());
    }

    private static JsonNode findExposure(JsonNode exposure, String lob) {
        List<JsonNode> matching = new ArrayList<>();
        exposure.get("items").forEach(i -> {
            if (lob.equals(i.get("lineOfBusiness").asText())) {
                matching.add(i);
            }
        });
        assertThat(matching).hasSize(1);
        return matching.getFirst();
    }

    private static String token(String sub, String role, List<String> markets, String team) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(PlatformSecurityAutoConfiguration.devKey(SECRET)));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("claims-dev-idp").subject(sub)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600))
                .claim("roles", List.of(role)).claim("markets", markets);
        if (team != null) {
            claims.claim("team", team);
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    private static void await(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline && !condition.get()) {
            Thread.sleep(200);
        }
        assertThat(condition.get()).as("projection caught up within 30s").isTrue();
    }
}
