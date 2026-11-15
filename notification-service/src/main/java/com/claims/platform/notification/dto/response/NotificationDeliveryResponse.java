package com.claims.platform.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A delivery log entry as staff see it; the address is masked")
public record NotificationDeliveryResponse(
        UUID id,
        String recipientId,
        @Schema(example = "CUSTOMER", allowableValues = {"CUSTOMER", "HANDLER", "TEAM_LEAD"}) String audience,
        @Schema(example = "EMAIL", allowableValues = {"IN_APP", "EMAIL", "SMS", "PUSH"}) String channel,
        @Schema(description = "Masked e-mail address or phone number", example = "p***@example.com") String address,
        @Schema(example = "en") String language,
        @Schema(description = "The event that caused the message", example = "ClaimApproved") String type,
        String subject,
        @Schema(example = "SENT", allowableValues = {"PENDING", "SENT", "DELIVERED", "BOUNCED", "FAILED"}) String status,
        @Schema(description = "Send attempts so far", example = "1") int attempts,
        @Schema(description = "Provider error of the last failed attempt") String lastError,
        Instant sentAt,
        Instant readAt) {
}
