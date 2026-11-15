package com.claims.platform.notification;

import com.claims.platform.common.web.Headers;
import com.claims.platform.testsupport.TestPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
class NotificationServiceIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestPostgres.registerFreshDatabase(registry);
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthIsPublicAndUpIncludingDatabaseAndEchoesCorrelationId() {
        ResponseEntity<String> response = http.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"").contains("\"db\"");
        assertThat(response.getHeaders().getFirst(Headers.CORRELATION_ID)).isNotBlank();
    }

    @Test
    void everythingElseRequiresABearerTokenAndAnswersProblemJson() {
        ResponseEntity<String> response = http.getForEntity("/anything", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
        assertThat(response.getBody()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void flywayBaselineApplied() {
        assertThat(jdbc.queryForObject(
                "select count(*) from notification.flyway_schema_history where success", Integer.class)).isPositive();
    }
}
