package com.claims.platform.insights.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Open claims of one handler")
public record WorkloadResponse(
        String handlerId,
        @Schema(example = "7") int openClaims,
        @Schema(description = "Open claims per status", example = "{\"ASSIGNED\": 2, \"UNDER_ASSESSMENT\": 5}") Map<String, Integer> byStatus,
        @Schema(description = "Submission time of the handler's oldest open claim") Instant oldestSubmittedAt) {
}
