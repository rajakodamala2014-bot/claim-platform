package com.claims.platform.claims.platform.dto.response;

/** A person the screens can name. {@code role} is for choosing who can be assigned; it is not shown. */
public record PersonResponse(String id, String name, String role) {
}
