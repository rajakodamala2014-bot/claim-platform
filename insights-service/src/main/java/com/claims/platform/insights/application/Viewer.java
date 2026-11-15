package com.claims.platform.insights.application;

import java.util.Set;

/** The staff member asking: scopes every query to their markets (and team where relevant). */
public record Viewer(String id, String role, Set<String> markets, String teamId) {
    public static final Set<String> STAFF = Set.of("HANDLER", "TEAM_LEAD", "MANAGER");

    public Viewer {
        markets = Set.copyOf(markets);
    }

    public boolean isSupervisor() {
        return "TEAM_LEAD".equals(role) || "MANAGER".equals(role);
    }

    public boolean isManager() {
        return "MANAGER".equals(role);
    }

    /** The requested market (must be one of mine), or all my markets when none was asked for. */
    public Set<String> marketsFor(String requested) {
        if (requested == null || requested.isBlank()) {
            return markets;
        }
        String market = requested.trim().toUpperCase();
        if (!markets.contains(market)) {
            throw new InsightsException(InsightsException.Code.FORBIDDEN_MARKET, "You are not assigned to market " + market);
        }
        return Set.of(market);
    }

    /** The requested team, defaulting to my own; handlers only see their own team. */
    public String teamFor(String requested) {
        String team = requested == null || requested.isBlank() ? teamId : requested;
        if (team == null) {
            throw new InsightsException(InsightsException.Code.VALIDATION_FAILED, "team is required");
        }
        if (!isManager() && !team.equals(teamId)) {
            throw new InsightsException(InsightsException.Code.FORBIDDEN_ROLE, "You can only see your own team");
        }
        return team;
    }
}
