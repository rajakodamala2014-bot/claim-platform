package com.claims.platform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class JwtService {
    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;

    public JwtService(String secret, String issuer, Duration ttl) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.ttl = ttl == null ? Duration.ofHours(1) : ttl;
    }

    public IssuedToken issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(user.userId())
                .claim("email", user.email())
                .claim("name", user.name())
                .claim("role", user.role())
                .claim("market", user.market())
                .claim("markets", user.markets())
                .claim("teamId", user.teamId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(token, ttl.toSeconds());
    }

    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        @SuppressWarnings("unchecked")
        List<String> markets = claims.get("markets", List.class);
        return new AuthenticatedUser(
                claims.getSubject(),
                claims.get("email", String.class),
                claims.get("name", String.class),
                claims.get("role", String.class),
                claims.get("market", String.class),
                markets == null ? List.of() : List.copyOf(markets),
                claims.get("teamId", String.class)
        );
    }

    public record IssuedToken(String accessToken, long expiresIn) {
    }
}
