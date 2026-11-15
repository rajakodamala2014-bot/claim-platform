package com.claims.platform.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Whether a channel is switched on, and where to send to")
public record ChannelSettingRequest(
        @Schema(description = "Send notifications through this channel", example = "true") boolean enabled,
        @Schema(description = "E-mail address or phone number; not used for push", example = "priya@example.com")
        @Size(max = 200) String address) {
}
