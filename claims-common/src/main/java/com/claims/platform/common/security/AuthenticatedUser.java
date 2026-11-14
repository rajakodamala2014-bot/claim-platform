package com.claims.platform.common.security;

import java.util.List;

public record AuthenticatedUser(
        String userId,
        String email,
        String name,
        String role,
        String market,
        List<String> markets,
        String teamId
) {
    public boolean staff() {
        return "ADJUSTER".equals(role) || "SENIOR_ADJUSTER".equals(role) || "MANAGER".equals(role);
    }

    public boolean claimant() {
        return "CLAIMANT".equals(role);
    }

    public boolean hasMarket(String marketCode) {
        if (marketCode == null || markets == null) {
            return false;
        }
        return markets.stream().anyMatch(marketCode::equalsIgnoreCase);
    }
}
