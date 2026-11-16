package com.claims.platform.claims.platform.service;

import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.platform.dto.response.TokenResponse;
import com.claims.platform.claims.platform.entity.AppUser;
import com.claims.platform.claims.platform.repository.AppUserRepository;
import com.claims.platform.web.config.PlatformSecurityAutoConfiguration;
import com.claims.platform.web.config.PlatformSecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    /** The decoder validates expiry against the real clock, so the token must be issued "now", not on a fixed date. */
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final String PASSWORD = "ChangeMe123!";

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, passwords,
                new NimbusJwtEncoder(new ImmutableSecret<>(PlatformSecurityAutoConfiguration.devKey(SECRET))),
                new PlatformSecurityProperties(null, SECRET, "claims-dev-idp"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void loginIssuesATokenForTheStoredAccount() {
        when(users.findByUsername("arun")).thenReturn(Optional.of(user("h-arun", "arun", "HANDLER", List.of("SG"), "team-sg-motor", true)));

        TokenResponse token = auth.login("  Arun ", PASSWORD);

        assertThat(token.token_type()).isEqualTo("Bearer");
        assertThat(token.expires_in()).isEqualTo(8 * 60 * 60);
        assertThat(token.sub()).isEqualTo("h-arun");
        assertThat(token.role()).isEqualTo("HANDLER");
        Jwt jwt = decoder().decode(token.access_token());
        assertThat(jwt.getSubject()).isEqualTo("h-arun");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("HANDLER");
        assertThat(jwt.getClaimAsStringList("markets")).containsExactly("SG");
        assertThat(jwt.getClaimAsString("team")).isEqualTo("team-sg-motor");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Arun Kumar");
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(8 * 60 * 60));
    }

    @Test
    void managerTokenOmitsTheTeamClaim() {
        when(users.findByUsername("lee")).thenReturn(Optional.of(user("m-lee", "lee", "MANAGER",
                List.of("SG", "HK", "MY", "TH", "ID", "AU"), null, true)));

        Jwt jwt = decoder().decode(auth.login("lee", PASSWORD).access_token());

        assertThat(jwt.getClaims()).doesNotContainKey("team");
        assertThat(jwt.getClaimAsStringList("markets")).containsExactly("SG", "HK", "MY", "TH", "ID", "AU");
    }

    @Test
    void unknownUserWrongPasswordAndDisabledAccountLookTheSame() {
        when(users.findByUsername("priya")).thenReturn(Optional.of(user("cust-priya", "priya", "CLAIMANT", List.of("SG"), null, true)));
        when(users.findByUsername("ho")).thenReturn(Optional.of(user("tl-hk-ho", "ho", "TEAM_LEAD", List.of("HK"), "team-hk", false)));
        when(users.findByUsername("missing")).thenReturn(Optional.empty());

        assertRejected(() -> auth.login("priya", "nope"));
        assertRejected(() -> auth.login("missing", PASSWORD));
        assertRejected(() -> auth.login("ho", PASSWORD));
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(DomainException.class)
                .matches(error -> ((DomainException) error).code() == ErrorCode.UNAUTHENTICATED)
                .hasMessage("Unknown username or password");
    }

    private AppUser user(String id, String username, String role, List<String> markets, String team, boolean enabled) {
        return new AppUser(id, username, passwords.encode(PASSWORD), "Arun Kumar", role, markets, team, "en-SG", enabled);
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(PlatformSecurityAutoConfiguration.devKey(SECRET))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("claims-dev-idp"));
        return decoder;
    }
}
