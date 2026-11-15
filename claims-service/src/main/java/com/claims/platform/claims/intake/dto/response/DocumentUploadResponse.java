package com.claims.platform.claims.intake.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Where and how to upload the file: send it with the given method and headers before the URL expires")
public record DocumentUploadResponse(UUID uploadId, String uploadUrl, String method, Map<String, String> headers,
                             Instant expiresAt) {
}
