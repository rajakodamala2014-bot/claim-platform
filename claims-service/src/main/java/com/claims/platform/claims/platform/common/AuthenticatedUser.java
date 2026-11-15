package com.claims.platform.claims.platform.common;

import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.core.entity.Role;
import com.claims.platform.web.common.PlatformUser;
import org.springframework.security.oauth2.jwt.Jwt;

/** Turns the validated access token into the domain's {@link Actor}. */
public final class AuthenticatedUser {
    private AuthenticatedUser() {
    }

    public static PlatformUser from(Jwt jwt) {
        return PlatformUser.from(jwt);
    }

    public static Actor toActor(Jwt jwt) {
        PlatformUser user = from(jwt);
        return new Actor(user.id(), Role.valueOf(user.primaryRole()));
    }

    /** The claimant's home market (first in the token), used when the Policy system can't tell us. */
    public static MarketCode resolveHomeMarket(Jwt jwt) {
        return from(jwt).markets().stream().sorted().findFirst().map(MarketCode::of).orElse(null);
    }
}
