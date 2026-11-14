package com.claims.platform.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void roundTripKeepsRoleMarketAndTeam() {
        JwtService jwt = new JwtService("claims-platform-dev-secret-change-me!!", "claims-platform", Duration.ofHours(1));
        AuthenticatedUser user = new AuthenticatedUser(
                "u-2001", "arun@example.com", "Arun", "ADJUSTER", "SG", List.of("SG"), "t-1");
        String token = jwt.issue(user).accessToken();
        AuthenticatedUser parsed = jwt.parse(token);
        assertEquals("u-2001", parsed.userId());
        assertEquals("ADJUSTER", parsed.role());
        assertEquals("SG", parsed.market());
        assertEquals(List.of("SG"), parsed.markets());
        assertEquals("t-1", parsed.teamId());
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("short", "claims-platform", Duration.ofMinutes(5)));
    }
}
