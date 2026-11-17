package com.claims.platform.claims.platform.service;

/** The name shown on screen: Priya, Arun. Never the account id. */
public final class PersonNames {
    private PersonNames() {
    }

    public static String of(String username) {
        if (username == null || username.isBlank()) {
            return "";
        }
        String trimmed = username.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1).toLowerCase();
    }
}
