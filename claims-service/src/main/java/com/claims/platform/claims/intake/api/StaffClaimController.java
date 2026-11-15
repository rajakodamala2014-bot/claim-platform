package com.claims.platform.claims.intake.api;

import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.application.StaffViews.AuditEntry;
import com.claims.platform.claims.core.domain.Channel;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.intake.api.ClaimantRequests.ContactRequest;
import com.claims.platform.claims.intake.api.ClaimantRequests.IncidentRequest;
import com.claims.platform.claims.intake.api.ClaimantRequests.MoneyRequest;
import com.claims.platform.claims.intake.application.GetStaffClaim;
import com.claims.platform.claims.intake.application.SubmitClaim;
import com.claims.platform.claims.platform.api.StaffRequest;
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
@RequestMapping("/staff/v1/claims")
public class StaffClaimController {
    private final SubmitClaim submitClaim;
    private final GetStaffClaim getStaffClaim;

    public StaffClaimController(SubmitClaim submitClaim, GetStaffClaim getStaffClaim) {
        this.submitClaim = submitClaim;
        this.getStaffClaim = getStaffClaim;
    }

    /** Claim reported by phone/email. Documents are added later by the customer (uploads are owner-bound). */
    public record RegisterClaimRequest(
            @NotBlank @Size(max = 100) String claimantId,
            @NotBlank @Size(max = 200) String claimantName,
            @NotBlank @Size(max = 40) String policyNumber,
            @NotNull LineOfBusiness lineOfBusiness,
            @NotNull Instant incidentAt,
            @NotNull @Valid IncidentRequest incident,
            @Valid MoneyRequest claimedAmount,
            @Valid ContactRequest contact
    ) {
    }

    @PostMapping
    public ResponseEntity<SubmitClaim.Result> register(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestHeader(Headers.IDEMPOTENCY_KEY) UUID idempotencyKey,
                                                       @Valid @RequestBody RegisterClaimRequest body) {
        StaffContext staff = StaffRequest.staff(jwt);
        SubmitClaim.Command command = new SubmitClaim.Command(body.claimantId(), body.claimantName(),
                staff.markets().size() == 1 ? staff.markets().iterator().next() : null, Channel.STAFF,
                body.policyNumber(), body.lineOfBusiness(), body.incidentAt(), body.incident().toDomain(body.lineOfBusiness()),
                body.claimedAmount() == null ? null : body.claimedAmount().toMoney("claimedAmount"), List.of(),
                body.contact() == null ? null : body.contact().phone(), body.contact() == null ? null : body.contact().email());
        SubmitClaim.Result result = submitClaim.submit(staff.actor(), idempotencyKey, command, staff.markets());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/staff/v1/claims/" + result.claimId()))
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result);
    }

    @GetMapping("/{claimId}")
    public ResponseEntity<GetStaffClaim.Workspace> workspace(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
        GetStaffClaim.Workspace workspace = getStaffClaim.workspace(new ClaimId(claimId), StaffRequest.staff(jwt));
        return ResponseEntity.ok().eTag(StaffRequest.etag(workspace.claim().version())).body(workspace);
    }

    @GetMapping("/{claimId}/audit")
    public List<AuditEntry> audit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
        return getStaffClaim.audit(new ClaimId(claimId), StaffRequest.staff(jwt));
    }
}
