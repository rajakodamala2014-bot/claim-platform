package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A document attached to a claim")
public record StaffDocumentResponse(UUID documentId, String fileName, String contentType, long sizeBytes, String status,
                           Instant uploadedAt) {
}
