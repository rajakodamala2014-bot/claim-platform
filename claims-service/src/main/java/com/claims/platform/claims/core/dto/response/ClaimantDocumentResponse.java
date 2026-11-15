package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A document the customer uploaded")
public record ClaimantDocumentResponse(UUID documentId, String fileName, String contentType, Instant uploadedAt) {
}
