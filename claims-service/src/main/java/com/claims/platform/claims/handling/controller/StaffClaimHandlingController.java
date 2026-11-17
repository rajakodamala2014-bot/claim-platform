package com.claims.platform.claims.handling.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.claims.platform.claims.core.dto.response.CommandResponse;
import com.claims.platform.claims.core.dto.response.MoneyResponse;
import com.claims.platform.claims.handling.dto.response.DecisionResponse;
import com.claims.platform.claims.handling.dto.request.ReviewDecisionRequest;
import com.claims.platform.claims.handling.dto.request.ProposeDecisionRequest;
import com.claims.platform.claims.handling.dto.request.RecordAssessmentRequest;
import com.claims.platform.claims.handling.dto.request.CreateInformationRequest;
import com.claims.platform.claims.handling.dto.request.AssignClaimRequest;
import com.claims.platform.claims.handling.entity.ReviewAction;
import com.claims.platform.claims.core.service.StaffClaimCommandService.Outcome;
import com.claims.platform.claims.core.entity.ApprovalRequirement;
import com.claims.platform.claims.core.entity.Assessment;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.Decision;
import com.claims.platform.claims.core.exception.ValidationException;
import com.claims.platform.claims.handling.service.ClaimHandlingService;
import com.claims.platform.claims.platform.common.StaffRequestSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * S4-S10. Every call needs {@code If-Match: "<version>"} (412 STALE_VERSION if someone changed the claim
 * meanwhile) and returns the new version as ETag.
 */
@RestController
@Tag(name = "Staff claim handling", description = "Assignment, assessment, reserves, information requests and decisions. Every call needs If-Match and returns the new version as ETag")
@RequestMapping("/staff/v1/claims/{claimId}")
public class StaffClaimHandlingController {
    private final ClaimHandlingService claimHandlingService;

    public StaffClaimHandlingController(ClaimHandlingService claimHandlingService) {
        this.claimHandlingService = claimHandlingService;
    }

    /** S4. Empty body = pick up for myself. */
    @PostMapping("/assignment")
    @Operation(summary = "Pick up or assign a claim", description = "S4. An empty body means the caller takes the claim; supervisors may assign or reassign.")
    @ApiResponse(responseCode = "200", description = "Assigned; result is the handler id")
    @ApiResponse(responseCode = "400", description = "Invalid request body (VALIDATION_FAILED, with field errors)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not staff, or not assigned to the market of the claim (FORBIDDEN_MARKET, FORBIDDEN_ROLE)")
    @ApiResponse(responseCode = "404", description = "Unknown claim (CLAIM_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Someone else picked it up first (CLAIM_ALREADY_ASSIGNED) or the status does not allow it (CLAIM_INVALID_TRANSITION)")
    @ApiResponse(responseCode = "412", description = "The claim changed since the version in If-Match (STALE_VERSION)")
    @ApiResponse(responseCode = "428", description = "If-Match header is missing (IF_MATCH_REQUIRED)")
    public ResponseEntity<CommandResponse<String>> assignClaim(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId,
                                                          @Parameter(description = "Claim version the caller last read, as a quoted ETag", example = "\"3\"", required = true)
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                          @Valid @RequestBody(required = false) AssignClaimRequest body) {
        long version = StaffRequestSupport.parseExpectedVersion(ifMatch);
        return StaffRequestSupport.toResponseEntity(claimHandlingService.assignClaim(new ClaimId(claimId), StaffRequestSupport.toStaffContext(jwt), version,
                body == null ? null : body.assigneeId(), body == null ? null : body.teamId()));
    }

    /** S5. */
    @PostMapping("/start-assessment")
    @Operation(summary = "Start assessing a claim", description = "S5. ASSIGNED to UNDER_ASSESSMENT.")
    @ApiResponse(responseCode = "200", description = "Assessment started")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not staff, or not assigned to the market of the claim (FORBIDDEN_MARKET, FORBIDDEN_ROLE)")
    @ApiResponse(responseCode = "404", description = "Unknown claim (CLAIM_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The claim is not in a status that allows this (CLAIM_INVALID_TRANSITION) or changed concurrently (CONCURRENT_UPDATE)")
    @ApiResponse(responseCode = "412", description = "The claim changed since the version in If-Match (STALE_VERSION)")
    @ApiResponse(responseCode = "428", description = "If-Match header is missing (IF_MATCH_REQUIRED)")
    public ResponseEntity<CommandResponse<Void>> startAssessment(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId,
                                                       @Parameter(description = "Claim version the caller last read, as a quoted ETag", example = "\"3\"", required = true)
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        long version = StaffRequestSupport.parseExpectedVersion(ifMatch);
        return StaffRequestSupport.toResponseEntity(claimHandlingService.startAssessment(new ClaimId(claimId), StaffRequestSupport.toStaffContext(jwt), version));
    }

    /** S7. */
    @PostMapping("/info-requests")
    @Operation(summary = "Ask the customer for information", description = "S7. The claim waits in AWAITING_INFO and the SLA clock pauses. The due date defaults to the market setting.")
    @ApiResponse(responseCode = "201", description = "Information request created; result is its id")
    @ApiResponse(responseCode = "400", description = "Invalid request body (VALIDATION_FAILED, with field errors)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not staff, or not assigned to the market of the claim (FORBIDDEN_MARKET, FORBIDDEN_ROLE)")
    @ApiResponse(responseCode = "404", description = "Unknown claim (CLAIM_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The claim is not in a status that allows this (CLAIM_INVALID_TRANSITION) or changed concurrently (CONCURRENT_UPDATE)")
    @ApiResponse(responseCode = "412", description = "The claim changed since the version in If-Match (STALE_VERSION)")
    @ApiResponse(responseCode = "428", description = "If-Match header is missing (IF_MATCH_REQUIRED)")
    public ResponseEntity<CommandResponse<UUID>> requestInformation(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId,
                                                                    @Parameter(description = "Claim version the caller last read, as a quoted ETag", example = "\"3\"", required = true)
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                    @Valid @RequestBody CreateInformationRequest body) {
        long version = StaffRequestSupport.parseExpectedVersion(ifMatch);
        return StaffRequestSupport.toResponseEntity(HttpStatus.CREATED, claimHandlingService.requestInformation(new ClaimId(claimId),
                StaffRequestSupport.toStaffContext(jwt), version, body.items(), body.dueAt()));
    }

    /** S8. */
    @PostMapping("/assessments")
    @Operation(summary = "Record the assessment of a claim", description = "S8. Liability finding, assessed amount and notes.")
    @ApiResponse(responseCode = "201", description = "Assessment recorded; result is its id")
    @ApiResponse(responseCode = "400", description = "Invalid request body (VALIDATION_FAILED, with field errors)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not staff, or not assigned to the market of the claim (FORBIDDEN_MARKET, FORBIDDEN_ROLE)")
    @ApiResponse(responseCode = "404", description = "Unknown claim (CLAIM_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The claim is not in a status that allows this (CLAIM_INVALID_TRANSITION) or changed concurrently (CONCURRENT_UPDATE)")
    @ApiResponse(responseCode = "412", description = "The claim changed since the version in If-Match (STALE_VERSION)")
    @ApiResponse(responseCode = "428", description = "If-Match header is missing (IF_MATCH_REQUIRED)")
    public ResponseEntity<CommandResponse<UUID>> recordAssessment(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId,
                                                        @Parameter(description = "Claim version the caller last read, as a quoted ETag", example = "\"3\"", required = true)
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                        @Valid @RequestBody RecordAssessmentRequest body) {
        long version = StaffRequestSupport.parseExpectedVersion(ifMatch);
        return StaffRequestSupport.toResponseEntity(HttpStatus.CREATED, claimHandlingService.recordAssessment(new ClaimId(claimId), StaffRequestSupport.toStaffContext(jwt),
                version, body.liability(), body.assessedAmount().toMoney("assessedAmount"), body.notes()));
    }

    /** S9. 200 when final within the proposer's authority, 202 when it waits for a team lead. */
    @PostMapping("/decisions")
    @Operation(summary = "Propose to approve or reject a claim", description = "S9. Within the authority limit of the proposer the decision is final at once; above it, it waits for a team lead.")
    @ApiResponse(responseCode = "200", description = "Decision is final (claim APPROVED or REJECTED)")
    @ApiResponse(responseCode = "202", description = "Decision awaits approval by a team lead (claim DECISION_PENDING)")
    @ApiResponse(responseCode = "400", description = "Invalid request body (VALIDATION_FAILED, with field errors)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not staff, or not assigned to the market of the claim (FORBIDDEN_MARKET, FORBIDDEN_ROLE)")
    @ApiResponse(responseCode = "404", description = "Unknown claim (CLAIM_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The claim is not in a status that allows this (CLAIM_INVALID_TRANSITION) or changed concurrently (CONCURRENT_UPDATE)")
    @ApiResponse(responseCode = "412", description = "The claim changed since the version in If-Match (STALE_VERSION)")
    @ApiResponse(responseCode = "428", description = "If-Match header is missing (IF_MATCH_REQUIRED)")
    public ResponseEntity<CommandResponse<DecisionResponse>> proposeDecision(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId,
                                                                  @Parameter(description = "Claim version the caller last read, as a quoted ETag", example = "\"3\"", required = true)
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                  @Valid @RequestBody ProposeDecisionRequest body) {
        long version = StaffRequestSupport.parseExpectedVersion(ifMatch);
        if (body.outcome() == Decision.Outcome.APPROVE
                && body.coverage() != Assessment.Liability.FULL && body.coverage() != Assessment.Liability.PARTIAL) {
            throw ValidationException.of("coverage", "must be FULL or PARTIAL");
        }
        if (body.outcome() == Decision.Outcome.APPROVE && body.coverage() == Assessment.Liability.PARTIAL && body.amount() == null) {
            throw ValidationException.of("amount", "is required when the claim is partially covered");
        }
        if (body.outcome() == Decision.Outcome.REJECT && (body.reasonCode() == null || body.reasonCode().isBlank())) {
            throw ValidationException.of("reasonCode", "is required to reject");
        }
        Outcome<Decision> outcome = claimHandlingService.proposeDecision(new ClaimId(claimId), StaffRequestSupport.toStaffContext(jwt), version,
                body.outcome(), body.coverage(), body.amount() == null ? null : body.amount().toMoney("amount"), body.reasonCode());
        Decision d = outcome.value();
        boolean waiting = d.approval() == ApprovalRequirement.SUPERVISOR_APPROVAL && d.status() == Decision.Status.AWAITING_APPROVAL;
        DecisionResponse result = new DecisionResponse(d.id(), d.outcome().name(), MoneyResponse.of(d.amount()),
                d.approval() == ApprovalRequirement.SUPERVISOR_APPROVAL, d.status().name());
        return StaffRequestSupport.toResponseEntity(waiting ? HttpStatus.ACCEPTED : HttpStatus.OK, map(outcome, result));
    }

    /** S10. Team lead approves or returns. */
    @PostMapping("/decisions/{decisionId}/approval")
    @Operation(summary = "Approve or return a pending decision", description = "S10. Team leads and managers only; the proposer cannot approve their own decision.")
    @ApiResponse(responseCode = "200", description = "Decision approved or returned")
    @ApiResponse(responseCode = "400", description = "Invalid request body (VALIDATION_FAILED, with field errors)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not allowed: wrong market or role, own decision, or amount above the approver's limit (FORBIDDEN_MARKET, FORBIDDEN_ROLE, SELF_APPROVAL_NOT_ALLOWED, AMOUNT_EXCEEDS_AUTHORITY)")
    @ApiResponse(responseCode = "404", description = "Unknown claim or decision (CLAIM_NOT_FOUND, DECISION_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The claim is not in a status that allows this (CLAIM_INVALID_TRANSITION) or changed concurrently (CONCURRENT_UPDATE)")
    @ApiResponse(responseCode = "412", description = "The claim changed since the version in If-Match (STALE_VERSION)")
    @ApiResponse(responseCode = "428", description = "If-Match header is missing (IF_MATCH_REQUIRED)")
    public ResponseEntity<CommandResponse<ReviewAction>> reviewDecision(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                                         @Parameter(description = "Claim id") @PathVariable UUID claimId, @Parameter(description = "Decision id") @PathVariable UUID decisionId,
                                                                         @Parameter(description = "Claim version the caller last read, as a quoted ETag", example = "\"3\"", required = true)
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                         @Valid @RequestBody ReviewDecisionRequest body) {
        long version = StaffRequestSupport.parseExpectedVersion(ifMatch);
        return StaffRequestSupport.toResponseEntity(claimHandlingService.reviewDecision(new ClaimId(claimId), decisionId, StaffRequestSupport.toStaffContext(jwt), version,
                body.action(), body.comment()));
    }

    private static <A, B> Outcome<B> map(Outcome<A> outcome, B value) {
        return new Outcome<>(value, outcome.claimId(), outcome.status(), outcome.version());
    }
}
