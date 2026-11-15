package com.claims.platform.testsupport;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * PostgreSQL 16 for integration tests. One server per JVM, a fresh database per test class (isolation without
 * restart cost).
 * <ul>
 *     <li>Docker available (CI) -> Testcontainers {@code postgres:16-alpine}.</li>
 *     <li>No Docker -> Zonky embedded PostgreSQL 16 (real postgres binaries, no container), so integration tests
 *     still run on developer machines instead of being skipped.</li>
 * </ul>
 * Set {@code -Dtest.postgres=testcontainers} to forbid the fallback.
 * <pre>
 * &#64;DynamicPropertySource
 * static void database(DynamicPropertyRegistry registry) { TestPostgres.registerFreshDatabase(registry); }
 * </pre>
 */
public final class TestPostgres {
    private static final String USER = "claims";
    private static final String PASSWORD = "claims";

    private static String adminUrl;
    private static String jdbcPrefix;
    private static String backend;

    private TestPostgres() {
    }

    /** Creates an empty database and points {@code spring.datasource.*} at it. */
    public static void registerFreshDatabase(DynamicPropertyRegistry registry) {
        String url = createDatabase("it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
    }

    /** "testcontainers" or "embedded" — handy in test output. */
    public static synchronized String backend() {
        start();
        return backend;
    }

    public static synchronized String createDatabase(String name) {
        start();
        try (Connection connection = DriverManager.getConnection(adminUrl, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name + " OWNER " + USER);
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not create test database " + name, ex);
        }
        return jdbcPrefix + name;
    }

    private static void start() {
        if (adminUrl != null) {
            return;
        }
        boolean forceContainers = "testcontainers".equals(System.getProperty("test.postgres"));
        if (forceContainers || isDockerAvailable()) {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withUsername(USER).withPassword(PASSWORD).withDatabaseName("postgres");
            container.start();
            jdbcPrefix = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/";
            backend = "testcontainers";
        } else {
            try {
                EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        postgres.close();
                    } catch (IOException ignored) {
                        // JVM is exiting
                    }
                }));
                try (Connection connection = postgres.getPostgresDatabase().getConnection();
                     Statement statement = connection.createStatement()) {
                    statement.execute("CREATE ROLE " + USER + " LOGIN SUPERUSER PASSWORD '" + PASSWORD + "'");
                }
                jdbcPrefix = "jdbc:postgresql://localhost:" + postgres.getPort() + "/";
                backend = "embedded";
            } catch (IOException ex) {
                throw new UncheckedIOException("Could not start embedded PostgreSQL", ex);
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }
        adminUrl = jdbcPrefix + "postgres";
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
