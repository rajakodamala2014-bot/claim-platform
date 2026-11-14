package com.claims.platform.identity.web;

public record TokenResponse(String accessToken, long expiresIn, User user) {
    public record User(String id, String role, String market) {
    }
}
