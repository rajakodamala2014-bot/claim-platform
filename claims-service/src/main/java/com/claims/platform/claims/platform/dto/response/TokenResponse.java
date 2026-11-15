package com.claims.platform.claims.platform.dto.response;

public record TokenResponse(String access_token, String token_type, long expires_in, String sub, String role) {
}
