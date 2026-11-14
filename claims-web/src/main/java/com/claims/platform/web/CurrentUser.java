package com.claims.platform.web;

import com.claims.platform.common.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static AuthenticatedUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw ApiException.unauthorized("Missing or invalid token");
        }
        return user;
    }

    public static void requireStaff() {
        if (!get().staff()) {
            throw ApiException.forbidden("Staff role is required");
        }
    }

    public static void requireClaimant() {
        if (!get().claimant()) {
            throw ApiException.forbidden("Claimant role is required");
        }
    }

    public static void requireMarket(String market) {
        AuthenticatedUser user = get();
        if (!user.hasMarket(market)) {
            throw ApiException.forbidden("You are not assigned to market " + market);
        }
    }
}
