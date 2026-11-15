package com.claims.platform.notification.dto.request;

import com.claims.platform.notification.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Delivery receipt sent by an e-mail / SMS / push provider")
public record DeliveryStatusRequest(
        @Schema(description = "The id the provider returned when it accepted the message",
                example = "fake-email-0b8e6a3e-6f0a-4c0e-9d0a-0d1f4f0c2a11") @NotBlank String providerMessageId,
        @Schema(description = "DELIVERED or BOUNCED", example = "DELIVERED") @NotNull Notification.Status status) {
}
