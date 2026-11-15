package com.claims.platform.claims.intake.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A damaged item of a property claim")
public record DamagedItemRequest(String description, MoneyRequest estimatedValue) {
}
