package com.claims.platform.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A customer's notification preferences")
public record NotificationPreferenceResponse(
        @Schema(description = "Language of the messages", example = "en") String language,
        ChannelSettingResponse email,
        ChannelSettingResponse sms,
        ChannelSettingResponse push) {
}
