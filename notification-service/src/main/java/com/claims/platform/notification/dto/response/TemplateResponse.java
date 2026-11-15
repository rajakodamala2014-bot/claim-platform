package com.claims.platform.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A message template")
public record TemplateResponse(
        @Schema(example = "ClaimApproved.CUSTOMER.en") String id,
        @Schema(example = "ClaimApproved") String eventType,
        @Schema(example = "CUSTOMER") String audience,
        @Schema(description = "Null when the template applies to any channel", example = "EMAIL") String channel,
        @Schema(example = "en") String language,
        String subject,
        String body) {
}
