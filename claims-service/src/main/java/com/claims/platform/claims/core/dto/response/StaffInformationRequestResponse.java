package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A request for information sent to the customer, with the answer when there is one")
public record StaffInformationRequestResponse(UUID requestId, List<String> items, Instant dueAt, String requestedBy, Instant requestedAt,
                          String status, String answer, List<UUID> documentIds, Instant closedAt) {
}
