package com.claims.platform.claims.platform.api;

import com.claims.platform.claims.core.domain.Actor;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.core.domain.Role;
import com.claims.platform.web.security.PlatformUser;
import org.springframework.security.oauth2.jwt.Jwt;

/** Turns the validated access token into the domain's {@link Actor}. */
public final class CurrentUser {
    private CurrentUser() {
    }

    public static PlatformUser of(Jwt jwt) {
        return PlatformUser.from(jwt);
    }

    public static Actor actor(Jwt jwt) {
        PlatformUser user = of(jwt);
        return new Actor(user.id(), Role.valueOf(user.primaryRole()));
    }

    /** The claimant's home market (first in the token), used when the Policy system can't tell us. */
    public static MarketCode homeMarket(Jwt jwt) {
        return of(jwt).markets().stream().sorted().findFirst().map(MarketCode::of).orElse(null);
    }
}
