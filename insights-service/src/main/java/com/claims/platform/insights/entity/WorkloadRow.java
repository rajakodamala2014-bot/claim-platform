package com.claims.platform.insights.entity;

import java.time.Instant;
import java.util.Map;

/** Open claims of one handler. */
public record WorkloadRow(String handlerId, int openClaims, Map<String, Integer> byStatus, Instant oldestSubmittedAt) {
}
