package com.claims.platform.identity.web;

import java.util.List;

public record UserResponse(
        String id,
        String name,
        String role,
        String market,
        List<String> markets,
        String teamId,
        String status
) {
}
