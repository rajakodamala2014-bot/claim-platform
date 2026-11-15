package com.claims.platform.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "A customer's notification preferences (replaces the stored ones)")
public record UpdatePreferenceRequest(
        @Schema(description = "Language of the messages", example = "en",
                allowableValues = {"en", "zh", "zh-Hant", "ms", "th", "id"})
        @NotBlank String language,
        @NotNull @Valid ChannelSettingRequest email,
        @NotNull @Valid ChannelSettingRequest sms,
        @NotNull @Valid ChannelSettingRequest push) {
}
