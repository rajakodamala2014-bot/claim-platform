package com.claims.platform.insights.entity;

/** Closed claims of one handler in a period. */
public record PerformanceRow(String handlerId, int closedClaims, double averageCycleHours, Double slaMetPercent,
                             double reopenRatePercent) {
}
