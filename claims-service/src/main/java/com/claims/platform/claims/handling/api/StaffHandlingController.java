package com.claims.platform.claims.handling.api;

import com.claims.platform.claims.core.application.ClaimantViews.MoneyView;
import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.domain.ApprovalRequirement;
import com.claims.platform.claims.core.domain.Assessment;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.Decision;
import com.claims.platform.claims.core.domain.ValidationException;
import com.claims.platform.claims.handling.application.AssignClaim;
import com.claims.platform.claims.handling.application.ProposeDecision;
import com.claims.platform.claims.handling.application.RecordAssessment;
import com.claims.platform.claims.handling.application.RequestInformation;
import com.claims.platform.claims.handling.application.ReviewDecision;
import com.claims.platform.claims.handling.application.SetReserve;
import com.claims.platform.claims.handling.application.StartAssessment;
import com.claims.platform.claims.platform.api.StaffRequest;
import com.claims.platform.claims.platform.api.StaffRequest.CommandResponse;
import com.claims.platform.common.money.Money;
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
@RequestMapping("/staff/v1/claims/{claimId}")
public class StaffHandlingController {
    private final AssignClaim assignClaim;
    private final StartAssessment startAssessment;
    private final SetReserve setReserve;
    private final RequestInformation requestInformation;
    private final RecordAssessment recordAssessment;
    private final ProposeDecision proposeDecision;
    private final ReviewDecision reviewDecision;

    public StaffHandlingController(AssignClaim assignClaim, StartAssessment startAssessment, SetReserve setReserve,
                                   RequestInformation requestInformation, RecordAssessment recordAssessment,
                                   ProposeDecision proposeDecision, ReviewDecision reviewDecision) {
        this.assignClaim = assignClaim;
        this.startAssessment = startAssessment;
        this.setReserve = setReserve;
        this.requestInformation = requestInformation;
        this.recordAssessment = recordAssessment;
        this.proposeDecision = proposeDecision;
        this.reviewDecision = reviewDecision;
    }

    public record MoneyRequest(@NotBlank String amount, @NotBlank @Size(min = 3, max = 3) String currency) {
        Money toMoney(String field) {
            try {
                return Money.of(amount, currency);
            } catch (IllegalArgumentException ex) {
                throw ValidationException.of(field, ex.getMessage());
            }
        }
    }

    public record AssignmentRequest(@Size(max = 100) String assigneeId, @Size(max = 100) String teamId) {
    }

    public record ReserveRequest(@NotNull @Valid MoneyRequest amount, @NotBlank @Size(max = 500) String reason) {
    }

    public record InformationRequestBody(@NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 300) String> items, Instant dueAt) {
    }

    public record AssessmentRequest(@NotNull Assessment.Liability liability, @NotNull @Valid MoneyRequest assessedAmount,
                                    @Size(max = 4000) String notes) {
    }

    public record DecisionRequest(@NotNull Decision.Outcome outcome, @Valid MoneyRequest amount, @Size(max = 60) String reasonCode) {
    }

    public record ApprovalRequest(@NotNull ReviewDecision.Action action, @Size(max = 1000) String comment) {
    }

    public record DecisionResult(UUID decisionId, String outcome, MoneyView amount, boolean approvalRequired, String decisionStatus) {
    }

    /** S4. Empty body = pick up for myself. */
    @PostMapping("/assignment")
    public ResponseEntity<CommandResponse<String>> assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
                                                          @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                          @Valid @RequestBody(required = false) AssignmentRequest body) {
        long version = StaffRequest.expectedVersion(ifMatch);
        return StaffRequest.respond(assignClaim.assign(new ClaimId(claimId), StaffRequest.staff(jwt), version,
                body == null ? null : body.assigneeId(), body == null ? null : body.teamId()));
    }

    /** S5. */
    @PostMapping("/start-assessment")
    public ResponseEntity<CommandResponse<Void>> start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
                                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        long version = StaffRequest.expectedVersion(ifMatch);
        return StaffRequest.respond(startAssessment.start(new ClaimId(claimId), StaffRequest.staff(jwt), version));
    }

    /** S6. Sets the new total; returns the outstanding reserve. */
    @PostMapping("/reserves")
    public ResponseEntity<CommandResponse<MoneyView>> reserve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
                                                              @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                              @Valid @RequestBody ReserveRequest body) {
        long version = StaffRequest.expectedVersion(ifMatch);
        Outcome<Money> outcome = setReserve.set(new ClaimId(claimId), StaffRequest.staff(jwt), version,
                body.amount().toMoney("amount"), body.reason());
        return StaffRequest.respond(map(outcome, MoneyView.of(outcome.value())));
    }

    /** S7. */
    @PostMapping("/info-requests")
    public ResponseEntity<CommandResponse<UUID>> requestInformation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
                                                                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                    @Valid @RequestBody InformationRequestBody body) {
        long version = StaffRequest.expectedVersion(ifMatch);
        return StaffRequest.respond(HttpStatus.CREATED, requestInformation.request(new ClaimId(claimId),
                StaffRequest.staff(jwt), version, body.items(), body.dueAt()));
    }

    /** S8. */
    @PostMapping("/assessments")
    public ResponseEntity<CommandResponse<UUID>> assess(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
                                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                        @Valid @RequestBody AssessmentRequest body) {
        long version = StaffRequest.expectedVersion(ifMatch);
        return StaffRequest.respond(HttpStatus.CREATED, recordAssessment.record(new ClaimId(claimId), StaffRequest.staff(jwt),
                version, body.liability(), body.assessedAmount().toMoney("assessedAmount"), body.notes()));
    }

    /** S9. 200 when final within the proposer's authority, 202 when it waits for a team lead. */
    @PostMapping("/decisions")
    public ResponseEntity<CommandResponse<DecisionResult>> decide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
                                                                  @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                  @Valid @RequestBody DecisionRequest body) {
        long version = StaffRequest.expectedVersion(ifMatch);
        if (body.outcome() == Decision.Outcome.APPROVE && body.amount() == null) {
            throw ValidationException.of("amount", "is required to approve");
        }
        Outcome<Decision> outcome = proposeDecision.propose(new ClaimId(claimId), StaffRequest.staff(jwt), version,
                body.outcome(), body.amount() == null ? null : body.amount().toMoney("amount"), body.reasonCode());
        Decision d = outcome.value();
        boolean waiting = d.approval() == ApprovalRequirement.SUPERVISOR_APPROVAL && d.status() == Decision.Status.AWAITING_APPROVAL;
        DecisionResult result = new DecisionResult(d.id(), d.outcome().name(), MoneyView.of(d.amount()),
                d.approval() == ApprovalRequirement.SUPERVISOR_APPROVAL, d.status().name());
        return StaffRequest.respond(waiting ? HttpStatus.ACCEPTED : HttpStatus.OK, map(outcome, result));
    }

    /** S10. Team lead approves or returns. */
    @PostMapping("/decisions/{decisionId}/approval")
    public ResponseEntity<CommandResponse<ReviewDecision.Action>> review(@AuthenticationPrincipal Jwt jwt,
                                                                         @PathVariable UUID claimId, @PathVariable UUID decisionId,
                                                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                         @Valid @RequestBody ApprovalRequest body) {
        long version = StaffRequest.expectedVersion(ifMatch);
        return StaffRequest.respond(reviewDecision.review(new ClaimId(claimId), decisionId, StaffRequest.staff(jwt), version,
                body.action(), body.comment()));
    }

    private static <A, B> Outcome<B> map(Outcome<A> outcome, B value) {
        return new Outcome<>(value, outcome.claimId(), outcome.status(), outcome.version());
    }
}
