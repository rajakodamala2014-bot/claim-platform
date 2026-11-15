package com.claims.platform.claims.settlement.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.claims.platform.claims.core.dto.response.CommandResponse;
import com.claims.platform.claims.settlement.dto.response.SettlementInstructionResponse;
import com.claims.platform.claims.settlement.dto.request.RetrySettlementRequest;
import com.claims.platform.claims.settlement.dto.request.InstructSettlementRequest;
import com.claims.platform.claims.core.service.StaffClaimCommandService.Outcome;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.Payee;
import com.claims.platform.claims.core.entity.Settlement;
import com.claims.platform.claims.platform.common.StaffRequestSupport;
import com.claims.platform.claims.settlement.service.SettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

import java.util.UUID;

/** S11 instruct payment, S12 retry a failed payment. If-Match required. */
@RestController
@Tag(name = "Staff settlements", description = "Paying an approved claim. Every call needs If-Match and returns the new version as ETag")
@RequestMapping("/staff/v1/claims/{claimId}/settlements")
public class StaffSettlementController {
    private final SettlementService settlementService;

    public StaffSettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping
    @Operation(summary = "Instruct payment of an approved claim", description = "S11. Sends a settlement request to the Payments system; the claim is SETTLED when the payment result arrives.")
    @ApiResponse(responseCode = "201", description = "Settlement instructed")
    @ApiResponse(responseCode = "400", description = "Invalid request body (VALIDATION_FAILED, with field errors)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not staff, or not assigned to the market of the claim (FORBIDDEN_MARKET, FORBIDDEN_ROLE)")
    @ApiResponse(responseCode = "404", description = "Unknown claim (CLAIM_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The claim is not in a status that allows this (CLAIM_INVALID_TRANSITION) or changed concurrently (CONCURRENT_UPDATE)")
    @ApiResponse(responseCode = "412", description = "The claim changed since the version in If-Match (STALE_VERSION)")
    @ApiResponse(responseCode = "428", description = "If-Match header is missing (IF_MATCH_REQUIRED)")
    @ApiResponse(responseCode = "422", description = "Amount above the approved amount or in another currency (SETTLEMENT_EXCEEDS_APPROVED, CURRENCY_MISMATCH)")
    public ResponseEntity<CommandResponse<SettlementInstructionResponse>> instructSettlement(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId,
                                                                      @Parameter(description = "Claim version the caller last read, as a quoted ETag", example = "\"3\"", required = true)
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                      @Valid @RequestBody InstructSettlementRequest body) {
        long version = StaffRequestSupport.parseExpectedVersion(ifMatch);
        Outcome<Settlement> outcome = settlementService.instructSettlement(new ClaimId(claimId), StaffRequestSupport.toStaffContext(jwt), version,
                body.payee().toPayee(), body.amount().toMoney("amount"));
        return StaffRequestSupport.toResponseEntity(HttpStatus.CREATED, map(outcome));
    }

    @PostMapping("/{settlementId}/retry")
    @Operation(summary = "Retry a failed payment", description = "S12. Optionally with corrected payee details; a new instruction id is used for every attempt.")
    @ApiResponse(responseCode = "200", description = "Payment instructed again")
    @ApiResponse(responseCode = "400", description = "Invalid request body (VALIDATION_FAILED, with field errors)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Not staff, or not assigned to the market of the claim (FORBIDDEN_MARKET, FORBIDDEN_ROLE)")
    @ApiResponse(responseCode = "404", description = "Unknown claim or settlement (CLAIM_NOT_FOUND, SETTLEMENT_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The payment did not fail, so there is nothing to retry (SETTLEMENT_NOT_RETRYABLE)")
    @ApiResponse(responseCode = "412", description = "The claim changed since the version in If-Match (STALE_VERSION)")
    @ApiResponse(responseCode = "428", description = "If-Match header is missing (IF_MATCH_REQUIRED)")
    public ResponseEntity<CommandResponse<SettlementInstructionResponse>> retrySettlement(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "Claim id") @PathVariable UUID claimId,
                                                                   @Parameter(description = "Settlement id") @PathVariable UUID settlementId,
                                                                   @Parameter(description = "Claim version the caller last read, as a quoted ETag", example = "\"3\"", required = true)
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                   @Valid @RequestBody(required = false) RetrySettlementRequest body) {
        long version = StaffRequestSupport.parseExpectedVersion(ifMatch);
        Payee corrected = body == null || body.payee() == null ? null : body.payee().toPayee();
        return StaffRequestSupport.toResponseEntity(map(settlementService.retrySettlement(new ClaimId(claimId), settlementId, StaffRequestSupport.toStaffContext(jwt),
                version, corrected)));
    }

    private static Outcome<SettlementInstructionResponse> map(Outcome<Settlement> outcome) {
        return new Outcome<>(SettlementInstructionResponse.of(outcome.value()), outcome.claimId(), outcome.status(), outcome.version());
    }
}
