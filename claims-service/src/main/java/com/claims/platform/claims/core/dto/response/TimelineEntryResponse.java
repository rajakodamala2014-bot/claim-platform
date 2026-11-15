package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "One customer-visible event in the history of a claim")
public record TimelineEntryResponse(Instant at, String type, String title, Map<String, Object> details) {
}
