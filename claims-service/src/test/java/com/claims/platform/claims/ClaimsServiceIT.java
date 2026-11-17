package com.claims.platform.claims;

import com.claims.platform.common.web.Headers;
import com.claims.platform.testsupport.TestPostgres;
import com.claims.platform.web.config.PlatformSecurityAutoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the whole service against PostgreSQL 16 (Testcontainers, or embedded when Docker is absent). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ClaimsServiceIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestPostgres.registerFreshDatabase(registry);
        com.claims.platform.testsupport.TestKafka.register(registry, ClaimsTopics.ALL);
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthIsPublicAndUpIncludingDatabase() {
        ResponseEntity<String> response = http.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"").contains("\"db\"");
        assertThat(http.getForEntity("/actuator/health/readiness", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/actuator/health/liveness", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void flywayCreatesModuleSchemasTablesAndSixMarkets() {
        var schemas = jdbc.queryForList("select schema_name from information_schema.schemata", String.class);

        assertThat(schemas).contains("platform", "core", "intake", "handling", "settlement");
        assertThat(jdbc.queryForList("select market_code from platform.market_config order by 1", String.class))
                .containsExactly("AU", "HK", "ID", "MY", "SG", "TH");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema in "
                + "('platform','core','intake','handling','settlement') and table_name <> 'flyway_schema_history'",
                Integer.class)).isEqualTo(18);
        assertThat(jdbc.queryForObject("select count(*) from platform.app_user", Integer.class)).isEqualTo(10);
    }

    @Test
    void correlationIdIsEchoed() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Headers.CORRELATION_ID, "it-corr-1");

        ResponseEntity<String> response = http.exchange("/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst(Headers.CORRELATION_ID)).isEqualTo("it-corr-1");
    }

    @Test
    void loginIssuesATokenTheServiceAcceptsAndRejectsTheWrongRole() {
        JsonNode token = login("priya", "ChangeMe123!");

        assertThat(token.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(token.get("expires_in").asLong()).isEqualTo(8 * 60 * 60);
        assertThat(token.get("sub").asText()).isEqualTo("cust-priya");
        assertThat(token.get("role").asText()).isEqualTo("CUSTOMER");
        Jwt jwt = decoder().decode(token.get("access_token").asText());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jwt.getClaimAsStringList("markets")).containsExactly("SG");
        assertThat(jwt.getClaims()).doesNotContainKey("team");

        assertThat(call("/claimant/v1/claims", token.get("access_token").asText()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call("/staff/v1/claims/00000000-0000-0000-0000-000000000001", token.get("access_token").asText()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode handler = login("Arun", "ChangeMe123!");
        Jwt handlerJwt = decoder().decode(handler.get("access_token").asText());
        assertThat(handlerJwt.getSubject()).isEqualTo("h-arun");
        assertThat(handlerJwt.getClaimAsString("team")).isEqualTo("team-sg-motor");
        assertThat(call("/claimant/v1/claims", handler.get("access_token").asText()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void badPasswordUnknownUserAndDisabledAccountAreTheSame401() {
        JsonNode wrong = rejectedLogin("priya", "wrong-password");
        JsonNode unknown = rejectedLogin("nobody", "ChangeMe123!");

        assertThat(wrong.get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(unknown.get("code").asText()).isEqualTo(wrong.get("code").asText());
        assertThat(unknown.get("detail").asText()).isEqualTo(wrong.get("detail").asText());

        jdbc.update("update platform.app_user set enabled = false where username = 'ho'");
        try {
            JsonNode disabled = rejectedLogin("ho", "ChangeMe123!");
            assertThat(disabled.get("code").asText()).isEqualTo("UNAUTHENTICATED");
            assertThat(disabled.get("detail").asText()).isEqualTo(wrong.get("detail").asText());
        } finally {
            jdbc.update("update platform.app_user set enabled = true where username = 'ho'");
        }
    }

    @Test
    void loginWithoutAPasswordIs400() {
        ResponseEntity<String> response = http.postForEntity("/auth/login",
                new HttpEntity<>("{\"username\":\"priya\"}", jsonHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"VALIDATION_FAILED\"").contains("password");
    }

    private JsonNode login(String username, String password) {
        ResponseEntity<JsonNode> response = http.postForEntity("/auth/login",
                java.util.Map.of("username", username, "password", password), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private JsonNode rejectedLogin(String username, String password) {
        ResponseEntity<JsonNode> response = http.postForEntity("/auth/login",
                java.util.Map.of("username", username, "password", password), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        return response.getBody();
    }

    private ResponseEntity<String> call(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(PlatformSecurityAutoConfiguration.devKey(
                        "local-dev-only-secret-change-me-0123456789abcdef"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("claims-dev-idp"));
        return decoder;
    }

    @Test
    void unknownPathsAreDeniedWithProblemJson() {
        ResponseEntity<String> response = http.getForEntity("/internal/anything", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
    }
}
