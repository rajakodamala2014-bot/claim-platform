package com.claims.platform.claims.platform.entity;

import java.util.List;

/** A person who can sign in. {@code id} is the token subject the rest of the platform already uses. */
public record AppUser(
        String id,
        String username,
        String passwordHash,
        String displayName,
        String role,
        List<String> markets,
        String teamId,
        String locale,
        boolean enabled) {
}
