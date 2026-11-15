package com.claims.platform.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "An in-app message as the customer sees it")
public record InboxNotificationResponse(
        UUID id,
        UUID claimId,
        @Schema(example = "SG-MTR-2026-000123") String claimNumber,
        @Schema(description = "The event that caused the message", example = "ClaimApproved") String type,
        @Schema(example = "Your claim is approved") String title,
        String message,
        @Schema(description = "When the message was created") Instant at,
        @Schema(description = "Whether the customer has opened it") boolean read) {
}
