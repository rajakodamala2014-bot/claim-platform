package com.claims.platform.claims;

import com.claims.platform.common.web.Headers;
import com.claims.platform.testsupport.TestPostgres;
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
                Integer.class)).isEqualTo(17);
    }

    @Test
    void correlationIdIsEchoed() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Headers.CORRELATION_ID, "it-corr-1");

        ResponseEntity<String> response = http.exchange("/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst(Headers.CORRELATION_ID)).isEqualTo("it-corr-1");
    }

    @Test
    void unknownPathsAreDeniedWithProblemJson() {
        ResponseEntity<String> response = http.getForEntity("/internal/anything", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
    }
}
