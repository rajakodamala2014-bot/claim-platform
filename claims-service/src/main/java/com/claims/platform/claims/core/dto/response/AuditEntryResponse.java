package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "One event of the audit trail of a claim, including internal ones")
public record AuditEntryResponse(UUID eventId, Instant at, String type, String visibility, String actorId, String actorRole,
                         Map<String, Object> data) {
}
