package com.claims.platform.claims.intake.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.claims.platform.claims.intake.dto.request.IncidentRequest;
import com.claims.platform.claims.intake.dto.request.ContactRequest;
import com.claims.platform.claims.core.dto.response.AuditEntryResponse;
import com.claims.platform.claims.intake.dto.request.RegisterClaimRequest;
import com.claims.platform.claims.intake.dto.response.StaffClaimWorkspaceResponse;
import com.claims.platform.claims.intake.dto.response.SubmitClaimResponse;
import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.entity.Channel;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.intake.service.ClaimQueryService;
import com.claims.platform.claims.intake.service.ClaimIntakeService;
import com.claims.platform.claims.platform.common.StaffRequestSupport;
import com.claims.platform.common.web.Headers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** S1 register on the customer's behalf, S2 claim workspace, S3 audit trail. */
@RestController
@Tag(name = "Staff claims", description = "Registering claims for customers and reading a claim with its audit trail")
@RequestMapping("/staff/v1/claims")
public class StaffClaimController {
    private final ClaimIntakeService claimIntakeService;
    private final ClaimQueryService claimQueryService;

    public StaffClaimController(ClaimIntakeService claimIntakeService, ClaimQueryService claimQueryService) {
        this.claimIntakeService = claimIntakeService;
        this.claimQueryService = claimQueryService;
    }

    @PostMapping
    @Operation(summary = "Register a claim for a customer", description = "S1. Same rules as a customer submission (cover check, Idempotency-Key), limited to the markets of the caller.")
    @ApiResponse(responseCode = "201", description = "Claim created; Location points at it")
    @ApiResponse(responseCode = "400", description = "Invalid body (VALIDATION_FAILED) or missing Idempotency-Key (IDEMPOTENCY_KEY_REQUIRED)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not staff, or the policy is in a market the caller is not assigned to (FORBIDDEN_MARKET)")
    @ApiResponse(responseCode = "422", description = "Policy not found or not in force, market not supported, or the Idempotency-Key was used for a different request")
    public ResponseEntity<SubmitClaimResponse> registerClaim(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                       @Parameter(description = "Unique per submission; a replay returns the original result")
            @RequestHeader(Headers.IDEMPOTENCY_KEY) UUID idempotencyKey,
                                                       @Valid @RequestBody RegisterClaimRequest body) {
        StaffContext staff = StaffRequestSupport.toStaffContext(jwt);
        ClaimIntakeService.SubmitClaimCommand command = new ClaimIntakeService.SubmitClaimCommand(body.claimantId(), body.claimantName(),
                staff.markets().size() == 1 ? staff.markets().iterator().next() : null, Channel.STAFF,
                body.policyNumber(), body.lineOfBusiness(), body.incidentAt(), body.incident().toIncidentDetails(body.lineOfBusiness()),
                body.claimedAmount() == null ? null : body.claimedAmount().toMoney("claimedAmount"), List.of(),
                body.contact() == null ? null : body.contact().phone(), body.contact() == null ? null : body.contact().email());
        SubmitClaimResponse result = claimIntakeService.registerClaimForCustomer(staff.actor(), idempotencyKey, command, staff.markets());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/staff/v1/claims/" + result.claimId()))
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result);
    }

    @GetMapping("/{claimId}")
    @Operation(summary = "Get a claim with everything a handler needs", description = "S2. Claim, reserve ledger, assessment, decision, settlement, documents, incident, parties and policy snapshot. The ETag is the version to send as If-Match.")
    @ApiResponse(responseCode = "200", description = "The claim workspace")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not staff, or not assigned to the market of the claim (FORBIDDEN_MARKET, FORBIDDEN_ROLE)")
    @ApiResponse(responseCode = "404", description = "Unknown claim (CLAIM_NOT_FOUND)")
    public ResponseEntity<StaffClaimWorkspaceResponse> getClaimWorkspace(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId) {
        StaffClaimWorkspaceResponse workspace = claimQueryService.getStaffClaimWorkspace(new ClaimId(claimId), StaffRequestSupport.toStaffContext(jwt));
        return ResponseEntity.ok().eTag(StaffRequestSupport.toEtag(workspace.claim().version())).body(workspace);
    }

    @GetMapping("/{claimId}/audit")
    @Operation(summary = "Get the full audit trail of a claim", description = "S3. Every event, including internal ones.")
    @ApiResponse(responseCode = "200", description = "Audit entries, oldest first")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not staff, or not assigned to the market of the claim (FORBIDDEN_MARKET, FORBIDDEN_ROLE)")
    @ApiResponse(responseCode = "404", description = "Unknown claim (CLAIM_NOT_FOUND)")
    public List<AuditEntryResponse> getClaimAudit(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId) {
        return claimQueryService.getStaffClaimAudit(new ClaimId(claimId), StaffRequestSupport.toStaffContext(jwt));
    }
}
