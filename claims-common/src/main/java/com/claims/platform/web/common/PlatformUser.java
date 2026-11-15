package com.claims.platform.web.common;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The caller, as asserted by the identity provider. Token claims:
 * {@code sub}, {@code roles} (CLAIMANT | HANDLER | TEAM_LEAD | MANAGER), {@code markets} (e.g. ["SG","MY"]),
 * {@code name}, {@code locale}, {@code team}.
 */
public record PlatformUser(String id, Set<String> roles, Set<String> markets, String name, String locale, String teamId) {
    public static final String CLAIMANT = "CLAIMANT";
    public static final String HANDLER = "HANDLER";
    public static final String TEAM_LEAD = "TEAM_LEAD";
    public static final String MANAGER = "MANAGER";

    public PlatformUser {
        roles = Set.copyOf(roles);
        markets = markets.stream().map(m -> m.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public static PlatformUser from(Jwt jwt) {
        return new PlatformUser(jwt.getSubject(), Set.copyOf(list(jwt, "roles")), Set.copyOf(list(jwt, "markets")),
                jwt.getClaimAsString("name"), jwt.getClaimAsString("locale"), jwt.getClaimAsString("team"));
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean canAccessMarket(String market) {
        return market != null && markets.contains(market.toUpperCase(Locale.ROOT));
    }

    /** Highest-privilege role, used when a single role is needed (e.g. authority limits). */
    public String primaryRole() {
        for (String role : List.of(MANAGER, TEAM_LEAD, HANDLER, CLAIMANT)) {
            if (roles.contains(role)) {
                return role;
            }
        }
        throw new IllegalStateException("User " + id + " has no platform role");
    }

    private static List<String> list(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? List.of() : values;
    }
}
