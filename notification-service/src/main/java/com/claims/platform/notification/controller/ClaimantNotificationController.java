package com.claims.platform.notification.controller;

import com.claims.platform.notification.dto.request.UpdatePreferenceRequest;
import com.claims.platform.notification.dto.response.InboxNotificationResponse;
import com.claims.platform.notification.dto.response.ItemListResponse;
import com.claims.platform.notification.dto.response.NotificationPreferenceResponse;
import com.claims.platform.notification.entity.Preference;
import com.claims.platform.notification.exception.NotificationException;
import com.claims.platform.notification.mapper.NotificationMapper;
import com.claims.platform.notification.service.NotificationInboxService;
import com.claims.platform.notification.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** N1-N4: a customer's own inbox and notification preferences. */
@RestController
@Tag(name = "Claimant notifications", description = "A customer's own inbox and notification preferences")
public class ClaimantNotificationController {
    private final NotificationInboxService inboxService;
    private final NotificationPreferenceService preferenceService;

    public ClaimantNotificationController(NotificationInboxService inboxService,
                                          NotificationPreferenceService preferenceService) {
        this.inboxService = inboxService;
        this.preferenceService = preferenceService;
    }

    @GetMapping("/claimant/v1/notifications")
    @Operation(summary = "List my in-app notifications", description = "N1. Newest first, at most 50.")
    @ApiResponse(responseCode = "200", description = "The caller's notifications")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    public ItemListResponse<InboxNotificationResponse> listInboxNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Only notifications that have not been read yet")
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return new ItemListResponse<>(inboxService.listInboxNotifications(jwt.getSubject(), unreadOnly).stream()
                .map(NotificationMapper::toInboxNotificationResponse).toList());
    }

    @PostMapping("/claimant/v1/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark one of my notifications as read", description = "N2.")
    @ApiResponse(responseCode = "204", description = "Marked as read")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    @ApiResponse(responseCode = "404", description = "No such notification in the caller's inbox (NOT_FOUND)")
    public void markNotificationRead(@AuthenticationPrincipal Jwt jwt,
                                     @Parameter(description = "Notification id") @PathVariable UUID id) {
        inboxService.markNotificationRead(jwt.getSubject(), id);
    }

    @GetMapping("/claimant/v1/notification-preferences")
    @Operation(summary = "Get my notification preferences", description = "N3. Defaults are returned until the customer saves their own.")
    @ApiResponse(responseCode = "200", description = "The caller's preferences")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    public NotificationPreferenceResponse getPreference(@AuthenticationPrincipal Jwt jwt) {
        return NotificationMapper.toPreferenceResponse(preferenceService.getPreference(jwt.getSubject()));
    }

    @PutMapping("/claimant/v1/notification-preferences")
    @Operation(summary = "Replace my notification preferences", description = "N4.")
    @ApiResponse(responseCode = "200", description = "The saved preferences")
    @ApiResponse(responseCode = "400", description = "Invalid body or unsupported language (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    public NotificationPreferenceResponse updatePreference(@AuthenticationPrincipal Jwt jwt,
                                                           @Valid @RequestBody UpdatePreferenceRequest request) {
        Preference preference;
        try {
            preference = NotificationMapper.toPreference(jwt.getSubject(), request);
        } catch (IllegalArgumentException ex) {
            throw new NotificationException(NotificationException.Code.VALIDATION_FAILED, ex.getMessage());
        }
        return NotificationMapper.toPreferenceResponse(preferenceService.updatePreference(preference));
    }
}
