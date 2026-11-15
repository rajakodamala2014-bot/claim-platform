package com.claims.platform.notification.controller;

import com.claims.platform.notification.common.HmacSignature;
import com.claims.platform.notification.dto.request.DeliveryStatusRequest;
import com.claims.platform.notification.dto.response.NotificationDeliveryResponse;
import com.claims.platform.notification.exception.NotificationException;
import com.claims.platform.notification.mapper.NotificationMapper;
import com.claims.platform.notification.service.DeliveryStatusService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** N7: delivery receipts from providers, authenticated by an HMAC signature instead of a user token. */
@RestController
@Tag(name = "Provider callbacks", description = "Delivery receipts from e-mail / SMS / push providers")
public class ProviderCallbackController {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DeliveryStatusService deliveryStatusService;
    private final byte[] callbackSecret;

    public ProviderCallbackController(DeliveryStatusService deliveryStatusService,
                                      @Value("${notifications.provider-callback-secret}") String callbackSecret) {
        if (callbackSecret == null || callbackSecret.length() < 32) {
            throw new IllegalStateException("notifications.provider-callback-secret must be at least 32 characters");
        }
        this.deliveryStatusService = deliveryStatusService;
        this.callbackSecret = callbackSecret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/internal/v1/providers/{provider}/delivery-status")
    @SecurityRequirements // no bearer token: the signature authenticates the provider
    @Operation(summary = "Record a provider's delivery receipt",
            description = "N7. Signed with X-Signature: sha256=<hex HMAC-SHA256 of the raw body>.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(schema = @Schema(implementation = DeliveryStatusRequest.class))))
    @ApiResponse(responseCode = "200", description = "The notification with its new status")
    @ApiResponse(responseCode = "400", description = "Body is not a delivery status, or the status is not allowed (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid signature (INVALID_SIGNATURE)")
    @ApiResponse(responseCode = "404", description = "The provider never accepted such a message (NOT_FOUND)")
    public NotificationDeliveryResponse recordDeliveryStatus(
            @Parameter(description = "Provider name", example = "fake-email") @PathVariable String provider,
            @Parameter(description = "sha256=<hex HMAC-SHA256 of the raw body>")
            @RequestHeader(value = HmacSignature.HEADER, required = false) String signature,
            @RequestBody String rawBody) {
        if (!HmacSignature.matches(callbackSecret, rawBody, signature)) {
            throw new NotificationException(NotificationException.Code.INVALID_SIGNATURE, "Missing or invalid X-Signature");
        }
        DeliveryStatusRequest request = parse(rawBody);
        return NotificationMapper.toDeliveryResponse(
                deliveryStatusService.recordDeliveryStatus(provider, request.providerMessageId(), request.status()));
    }

    /** The body is read raw (the signature covers its exact bytes) and parsed here. */
    private static DeliveryStatusRequest parse(String rawBody) {
        try {
            return JSON.readValue(rawBody, DeliveryStatusRequest.class);
        } catch (JsonProcessingException ex) {
            throw new NotificationException(NotificationException.Code.VALIDATION_FAILED, "Body is not a delivery status");
        }
    }
}
