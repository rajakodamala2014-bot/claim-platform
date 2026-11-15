package com.claims.platform.claims.platform.infrastructure;

import com.claims.platform.web.security.PlatformSecurityAutoConfiguration;
import com.claims.platform.web.security.PlatformSecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * LOCAL ONLY ('dev' profile): stands in for the identity provider (customer OTP / staff SSO). Issues HS256
 * tokens with the same claims a real IdP would: sub, roles, markets, name, locale, team.
 * <pre>POST /dev/token {"username":"priya"}</pre>
 */
@RestController
@Profile("dev")
public class DevTokenController {
    private static final Duration TTL = Duration.ofHours(8);

    /**
     * Demo personas for the six markets. Customers have motor + property policies in the Policy system stub
     * (docker/policy-system); staff match notification-service's seeded staff directory.
     */
    static final Map<String, Persona> PERSONAS = Map.ofEntries(
            // Singapore
            Map.entry("priya", new Persona("cust-priya", "CLAIMANT", List.of("SG"), "Priya Nair", "en-SG", null)),
            Map.entry("tan", new Persona("cust-tan", "CLAIMANT", List.of("SG"), "Tan Wei Ling", "zh-SG", null)),
            Map.entry("arun", new Persona("h-arun", "HANDLER", List.of("SG"), "Arun Kumar", "en-SG", "team-sg-motor")),
            Map.entry("kumar", new Persona("h-kumar", "HANDLER", List.of("SG"), "Kumar Raj", "en-SG", "team-sg-motor")),
            Map.entry("meera", new Persona("tl-meera", "TEAM_LEAD", List.of("SG", "MY"), "Meera Shah", "en-SG", "team-sg-motor")),
            Map.entry("lee", new Persona("m-lee", "MANAGER", List.of("SG", "HK", "MY", "TH", "ID", "AU"), "Lee Chen", "en-SG", null)),
            // Hong Kong
            Map.entry("wong", new Persona("cust-wong", "CLAIMANT", List.of("HK"), "Wong Siu Ming", "zh-HK", null)),
            Map.entry("chan", new Persona("h-hk-chan", "HANDLER", List.of("HK"), "Chan Ka Ming", "en-HK", "team-hk")),
            Map.entry("ho", new Persona("tl-hk-ho", "TEAM_LEAD", List.of("HK"), "Ho Wai Lam", "en-HK", "team-hk")),
            // Malaysia
            Map.entry("aisyah", new Persona("cust-aisyah", "CLAIMANT", List.of("MY"), "Nur Aisyah Binti Ahmad", "ms-MY", null)),
            Map.entry("faiz", new Persona("h-my-faiz", "HANDLER", List.of("MY"), "Faiz Rahman", "ms-MY", "team-my")),
            Map.entry("lim", new Persona("tl-my-lim", "TEAM_LEAD", List.of("MY"), "Lim Mei Ling", "en-MY", "team-my")),
            // Thailand
            Map.entry("somchai", new Persona("cust-somchai", "CLAIMANT", List.of("TH"), "Somchai Jaidee", "th-TH", null)),
            Map.entry("niran", new Persona("h-th-niran", "HANDLER", List.of("TH"), "Niran Suk", "th-TH", "team-th")),
            Map.entry("ploy", new Persona("tl-th-ploy", "TEAM_LEAD", List.of("TH"), "Ploy Chai", "th-TH", "team-th")),
            // Indonesia
            Map.entry("budi", new Persona("cust-budi", "CLAIMANT", List.of("ID"), "Budi Santoso", "id-ID", null)),
            Map.entry("dewi", new Persona("h-id-dewi", "HANDLER", List.of("ID"), "Dewi Lestari", "id-ID", "team-id")),
            Map.entry("agus", new Persona("tl-id-agus", "TEAM_LEAD", List.of("ID"), "Agus Santoso", "id-ID", "team-id")),
            // Australia
            Map.entry("olivia", new Persona("cust-olivia", "CLAIMANT", List.of("AU"), "Olivia Smith", "en-AU", null)),
            Map.entry("jack", new Persona("h-au-jack", "HANDLER", List.of("AU"), "Jack Wilson", "en-AU", "team-au")),
            Map.entry("emma", new Persona("tl-au-emma", "TEAM_LEAD", List.of("AU"), "Emma Brown", "en-AU", "team-au")));

    private final JwtEncoder encoder;
    private final String issuer;
    private final Clock clock;

    public DevTokenController(PlatformSecurityProperties properties, Clock clock) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(PlatformSecurityAutoConfiguration.devKey(properties.devSecret())));
        this.issuer = properties.devIssuer();
        this.clock = clock;
    }

    record Persona(String sub, String role, List<String> markets, String name, String locale, String team) {
    }

    /** Either {@code username} of a persona, or a custom identity (sub + role + markets). */
    public record TokenRequest(String username, String sub, String role, List<String> markets, String name, String team) {
    }

    public record TokenResponse(String access_token, String token_type, long expires_in, String sub, String role) {
    }

    @PostMapping("/dev/token")
    public TokenResponse token(@RequestBody TokenRequest request) {
        Persona persona = request.username() != null ? PERSONAS.get(request.username().toLowerCase())
                : request.sub() != null && request.role() != null
                ? new Persona(request.sub(), request.role(), request.markets() == null ? List.of() : request.markets(),
                request.name() == null ? request.sub() : request.name(), "en", request.team())
                : null;
        if (persona == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown user; personas: " + PERSONAS.keySet());
        }
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(persona.sub())
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .claim("roles", List.of(persona.role()))
                .claim("markets", persona.markets())
                .claim("name", persona.name())
                .claim("locale", persona.locale());
        if (persona.team() != null) {
            claims.claim("team", persona.team());
        }
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
        return new TokenResponse(token, "Bearer", TTL.toSeconds(), persona.sub(), persona.role());
    }
}
