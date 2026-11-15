package com.claims.platform.claims.platform.service;

import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.platform.dto.response.TokenResponse;
import com.claims.platform.claims.platform.entity.AppUser;
import com.claims.platform.claims.platform.repository.AppUserRepository;
import com.claims.platform.web.config.PlatformSecurityProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Checks username and password, then issues the same HS256 access token every service already validates. */
@Service
public class AuthService {
    static final Duration TTL = Duration.ofHours(8);
    private static final String UNKNOWN = "Unknown username or password";

    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String issuer;
    private final Clock clock;

    public AuthService(AppUserRepository users, PasswordEncoder passwords, JwtEncoder encoder,
                       PlatformSecurityProperties properties, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.issuer = properties.devIssuer();
        this.clock = clock;
    }

    public TokenResponse login(String username, String password) {
        AppUser user = users.findByUsername(username == null ? "" : username.trim().toLowerCase())
                .filter(AppUser::enabled)
                .filter(found -> passwords.matches(password, found.passwordHash()))
                .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHENTICATED, UNKNOWN));
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.id())
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .claim("roles", List.of(user.role()))
                .claim("markets", user.markets())
                .claim("name", user.displayName())
                .claim("locale", user.locale());
        if (user.teamId() != null) {
            claims.claim("team", user.teamId());
        }
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
        return new TokenResponse(token, "Bearer", TTL.toSeconds(), user.id(), user.role());
    }
}
