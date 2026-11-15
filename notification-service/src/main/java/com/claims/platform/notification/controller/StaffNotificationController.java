package com.claims.platform.notification.controller;

import com.claims.platform.notification.dto.response.ItemListResponse;
import com.claims.platform.notification.dto.response.NotificationDeliveryResponse;
import com.claims.platform.notification.mapper.NotificationMapper;
import com.claims.platform.notification.service.NotificationInboxService;
import com.claims.platform.web.common.PlatformUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** N5: what was sent about a claim, for staff. */
@RestController
@Tag(name = "Staff notifications", description = "Delivery log of a claim")
public class StaffNotificationController {
    private final NotificationInboxService inboxService;

    public StaffNotificationController(NotificationInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping("/staff/v1/claims/{claimId}/notifications")
    @Operation(summary = "List the notifications sent about a claim",
            description = "N5. All recipients and channels, with delivery status. Addresses are masked.")
    @ApiResponse(responseCode = "200", description = "The claim's delivery log")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not staff, or not assigned to the claim's market (FORBIDDEN_MARKET)")
    @ApiResponse(responseCode = "404", description = "Unknown claim (NOT_FOUND)")
    public ItemListResponse<NotificationDeliveryResponse> listNotificationsOfClaim(
            @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId) {
        return new ItemListResponse<>(inboxService.listNotificationsOfClaim(claimId, PlatformUser.from(jwt).markets())
                .stream().map(NotificationMapper::toDeliveryResponse).toList());
    }
}
