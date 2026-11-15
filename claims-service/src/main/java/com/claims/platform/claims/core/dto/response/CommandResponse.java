package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a staff command; version is the new claim version, also returned as ETag")
public record CommandResponse<T>(java.util.UUID claimId, String status, long version, T result) {
}
