package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A request for information as the customer sees it")
public record ClaimantInformationRequestResponse(UUID requestId, List<String> items, Instant dueAt, Instant requestedAt,
                              String status, Instant answeredAt) {
}
