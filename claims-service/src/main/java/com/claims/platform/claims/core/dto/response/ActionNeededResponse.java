package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "What the customer still has to do: the open information request")
public record ActionNeededResponse(UUID requestId, List<String> items, Instant dueAt) {
}
