package com.claims.platform.insights.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Closed claims of one handler in the period")
public record PerformanceResponse(
        String handlerId,
        @Schema(example = "12") int closedClaims,
        @Schema(description = "Average hours from submission to closure", example = "52.4") double averageCycleHours,
        @Schema(description = "Share of decisions made within the SLA; null when there were none", example = "91.7") Double slaMetPercent,
        @Schema(description = "Share of closed claims that had been reopened", example = "8.3") double reopenRatePercent) {
}
