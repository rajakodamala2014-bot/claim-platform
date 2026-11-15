package com.claims.platform.claims.handling.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.claims.platform.claims.core.dto.response.ClaimantInformationRequestResponse;
import com.claims.platform.claims.handling.dto.response.InformationResponseResult;
import com.claims.platform.claims.handling.dto.response.InformationRequestListResponse;
import com.claims.platform.claims.handling.dto.request.InformationResponseRequest;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.handling.service.InformationRequestService;
import com.claims.platform.claims.platform.common.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** C7 and C8: information requests, from the customer's side. */
@RestController
@Tag(name = "Claimant information requests", description = "Questions and documents a handler asked the customer for")
@RequestMapping("/claimant/v1/claims/{claimId}/info-requests")
public class ClaimantInformationRequestController {
    private final InformationRequestService informationRequestService;

    public ClaimantInformationRequestController(InformationRequestService informationRequestService) {
        this.informationRequestService = informationRequestService;
    }

    @GetMapping
    @Operation(summary = "List the information requests on my claim", description = "C7.")
    @ApiResponse(responseCode = "200", description = "Information requests, open and answered")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    @ApiResponse(responseCode = "404", description = "No such claim among the caller's own claims (CLAIM_NOT_FOUND)")
    public InformationRequestListResponse listInformationRequests(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId) {
        return new InformationRequestListResponse(informationRequestService.listInformationRequests(new ClaimId(claimId), jwt.getSubject()));
    }

    @PostMapping("/{requestId}/responses")
    @Operation(summary = "Answer an information request", description = "C8. Text and / or uploaded documents; the claim goes back to UNDER_ASSESSMENT and the SLA clock resumes.")
    @ApiResponse(responseCode = "200", description = "The answer was recorded")
    @ApiResponse(responseCode = "400", description = "Invalid request body (VALIDATION_FAILED, with field errors)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    @ApiResponse(responseCode = "404", description = "Unknown claim, request or upload (CLAIM_NOT_FOUND, INFO_REQUEST_NOT_FOUND, UPLOAD_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The claim is not in a status that allows this (CLAIM_INVALID_TRANSITION) or changed concurrently (CONCURRENT_UPDATE)")
    public InformationResponseResult respondToInformationRequest(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId,
                                  @Parameter(description = "Information request id") @PathVariable UUID requestId, @Valid @RequestBody InformationResponseRequest body) {
        String status = informationRequestService.respondToInformationRequest(new ClaimId(claimId), requestId, AuthenticatedUser.toActor(jwt), body.answer(),
                body.uploadIds());
        return new InformationResponseResult(claimId, requestId, status);
    }
}
