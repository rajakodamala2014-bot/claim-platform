package com.claims.platform.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether a channel is switched on, and where it sends to")
public record ChannelSettingResponse(
        @Schema(example = "true") boolean enabled,
        @Schema(description = "E-mail address or phone number; null for push", example = "priya@example.com") String address) {
}
