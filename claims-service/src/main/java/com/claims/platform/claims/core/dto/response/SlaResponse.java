package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Decision deadline of a claim and whether the clock is paused while waiting for the customer")
public record SlaResponse(Instant dueAt, boolean paused, Instant pausedAt) {
}
