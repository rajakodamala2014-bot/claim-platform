package com.claims.platform.claims.intake.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.claims.platform.claims.intake.dto.request.AppealClaimRequest;
import com.claims.platform.claims.intake.dto.request.WithdrawClaimRequest;
import com.claims.platform.claims.intake.dto.request.SubmitClaimRequest;
import com.claims.platform.claims.core.dto.response.TimelineEntryResponse;
import com.claims.platform.claims.core.dto.response.ClaimantClaimResponse;
import com.claims.platform.claims.intake.dto.response.ClaimStatusResponse;
import com.claims.platform.claims.intake.dto.response.ClaimantClaimSummaryResponse;
import com.claims.platform.claims.intake.dto.response.ClaimantClaimDetailResponse;
import com.claims.platform.claims.intake.dto.response.SubmitClaimResponse;
import com.claims.platform.claims.core.common.Page;
import com.claims.platform.claims.core.common.PageRequest;
import com.claims.platform.claims.core.entity.Channel;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.intake.service.ClaimIntakeService;
import com.claims.platform.claims.intake.service.ClaimQueryService;
import com.claims.platform.claims.platform.common.AuthenticatedUser;
import com.claims.platform.common.web.Headers;
import com.claims.platform.web.common.PlatformUser;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** C3, C4, C5, C6, C9, C10. */
@RestController
@Tag(name = "Claimant claims", description = "A customer reports an incident and follows their own claims")
@RequestMapping("/claimant/v1/claims")
public class ClaimantClaimController {
    private final ClaimIntakeService claimIntakeService;
    private final ClaimQueryService claimQueryService;

    public ClaimantClaimController(ClaimIntakeService claimIntakeService, ClaimQueryService claimQueryService) {
        this.claimIntakeService = claimIntakeService;
        this.claimQueryService = claimQueryService;
    }

    /** C3. 201 with the claim number; a replay of the same Idempotency-Key returns the original result. */
    @PostMapping
    @Operation(summary = "Report an incident (FNOL)", description = "C3. Creates the claim and runs the cover check. If the Policy system is unavailable the claim is still created with cover PENDING. A replay of the same Idempotency-Key returns the original result with Idempotent-Replayed: true.")
    @ApiResponse(responseCode = "201", description = "Claim created; Location points at it")
    @ApiResponse(responseCode = "400", description = "Invalid body (VALIDATION_FAILED) or missing Idempotency-Key (IDEMPOTENCY_KEY_REQUIRED)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    @ApiResponse(responseCode = "422", description = "Policy not found or not in force, market not supported, or the Idempotency-Key was used for a different request (POLICY_NOT_FOUND, POLICY_NOT_IN_FORCE, MARKET_NOT_SUPPORTED, IDEMPOTENCY_KEY_REUSED, UPLOAD_REJECTED)")
    public ResponseEntity<SubmitClaimResponse> submitClaim(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                     @Parameter(description = "Unique per submission; a replay returns the original result")
            @RequestHeader(Headers.IDEMPOTENCY_KEY) UUID idempotencyKey,
                                                     @Valid @RequestBody SubmitClaimRequest body) {
        PlatformUser user = AuthenticatedUser.from(jwt);
        ClaimIntakeService.SubmitClaimCommand command = new ClaimIntakeService.SubmitClaimCommand(user.id(), user.name(), AuthenticatedUser.resolveHomeMarket(jwt),
                Channel.APP, body.policyNumber(), body.lineOfBusiness(), body.incidentAt(),
                body.incident().toIncidentDetails(body.lineOfBusiness()),
                body.claimedAmount() == null ? null : body.claimedAmount().toMoney("claimedAmount"),
                body.uploadIds(), body.contact() == null ? null : body.contact().phone(),
                body.contact() == null ? null : body.contact().email());
        SubmitClaimResponse result = claimIntakeService.submitClaim(AuthenticatedUser.toActor(jwt), idempotencyKey, command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/claimant/v1/claims/" + result.claimId()))
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result);
    }

    /** C4. */
    @GetMapping
    @Operation(summary = "List my claims", description = "C4. Newest first, cursor paginated.")
    @ApiResponse(responseCode = "200", description = "One page of the caller's claims")
    @ApiResponse(responseCode = "400", description = "Invalid cursor or limit (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    public Page<ClaimantClaimSummaryResponse> listClaims(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                       @Parameter(description = "nextCursor of the previous page") @RequestParam(required = false) String cursor,
                                       @Parameter(description = "Page size, 1-100 (default 20)") @RequestParam(required = false) Integer limit) {
        return claimQueryService.listClaimantClaims(jwt.getSubject(), PageRequest.of(cursor, limit));
    }

    /** C5. ETag carries the claim version. */
    @GetMapping("/{claimId}")
    @Operation(summary = "Get one of my claims", description = "C5. Customer-visible data only (never reserves, notes or handler ids). The ETag carries the claim version.")
    @ApiResponse(responseCode = "200", description = "The claim")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    @ApiResponse(responseCode = "404", description = "No such claim among the caller's own claims (CLAIM_NOT_FOUND)")
    public ResponseEntity<ClaimantClaimResponse> getClaim(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId) {
        ClaimantClaimDetailResponse detail = claimQueryService.getClaimantClaim(new ClaimId(claimId), jwt.getSubject());
        return ResponseEntity.ok().eTag("\"" + detail.version() + "\"").body(detail.claim());
    }

    /** C6. Customer-visible history only. */
    @GetMapping("/{claimId}/timeline")
    @Operation(summary = "Get the timeline of one of my claims", description = "C6. Customer-visible events only, oldest first.")
    @ApiResponse(responseCode = "200", description = "Timeline entries")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    @ApiResponse(responseCode = "404", description = "No such claim among the caller's own claims (CLAIM_NOT_FOUND)")
    public List<TimelineEntryResponse> getClaimTimeline(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId) {
        return claimQueryService.getClaimantClaimTimeline(new ClaimId(claimId), jwt.getSubject());
    }

    /** C9. */
    @PostMapping("/{claimId}/withdraw")
    @Operation(summary = "Withdraw my claim", description = "C9. Allowed until a decision is pending.")
    @ApiResponse(responseCode = "200", description = "The claim is WITHDRAWN")
    @ApiResponse(responseCode = "400", description = "Invalid request body (VALIDATION_FAILED, with field errors)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    @ApiResponse(responseCode = "404", description = "No such claim among the caller's own claims (CLAIM_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The claim is not in a status that allows this (CLAIM_INVALID_TRANSITION) or changed concurrently (CONCURRENT_UPDATE)")
    public ClaimStatusResponse withdrawClaim(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId,
                                   @Valid @RequestBody(required = false) WithdrawClaimRequest body) {
        String reason = body == null ? null : body.reason();
        return new ClaimStatusResponse(claimId, claimIntakeService.withdrawClaim(new ClaimId(claimId), AuthenticatedUser.toActor(jwt), reason));
    }

    /** C10. */
    @PostMapping("/{claimId}/appeal")
    @Operation(summary = "Appeal a rejected claim", description = "C10. Reopens the claim for assessment.")
    @ApiResponse(responseCode = "200", description = "The claim is back UNDER_ASSESSMENT")
    @ApiResponse(responseCode = "400", description = "Invalid request body (VALIDATION_FAILED, with field errors)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    @ApiResponse(responseCode = "404", description = "No such claim among the caller's own claims (CLAIM_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The claim is not in a status that allows this (CLAIM_INVALID_TRANSITION) or changed concurrently (CONCURRENT_UPDATE)")
    public ClaimStatusResponse appealClaim(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId,
                                 @Valid @RequestBody AppealClaimRequest body) {
        return new ClaimStatusResponse(claimId, claimIntakeService.appealClaim(new ClaimId(claimId), AuthenticatedUser.toActor(jwt), body.reason()));
    }
}
