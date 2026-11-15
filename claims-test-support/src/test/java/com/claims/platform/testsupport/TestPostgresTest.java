package com.claims.platform.testsupport;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class TestPostgresTest {

    @Test
    void providesAnIsolatedPostgres16Database() throws Exception {
        String first = TestPostgres.createDatabase("tp_first");
        String second = TestPostgres.createDatabase("tp_second");

        try (Connection a = DriverManager.getConnection(first, "claims", "claims");
             Connection b = DriverManager.getConnection(second, "claims", "claims");
             Statement sa = a.createStatement();
             Statement sb = b.createStatement()) {
            sa.execute("CREATE TABLE only_in_first (id int)");
            ResultSet version = sa.executeQuery("SHOW server_version_num");
            version.next();
            assertThat(version.getInt(1) / 10000).isEqualTo(16);
            ResultSet tables = sb.executeQuery(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'only_in_first'");
            tables.next();
            assertThat(tables.getInt(1)).isZero();
        }
        System.out.println("TestPostgres backend: " + TestPostgres.backend());
    }
}
