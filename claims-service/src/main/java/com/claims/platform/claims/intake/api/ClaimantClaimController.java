package com.claims.platform.claims.intake.api;

import com.claims.platform.claims.core.application.ClaimantViews.ClaimView;
import com.claims.platform.claims.core.application.ClaimantViews.TimelineEntry;
import com.claims.platform.claims.core.application.Page;
import com.claims.platform.claims.core.application.PageRequest;
import com.claims.platform.claims.core.domain.Channel;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.intake.api.ClaimantRequests.AppealRequest;
import com.claims.platform.claims.intake.api.ClaimantRequests.SubmitClaimRequest;
import com.claims.platform.claims.intake.api.ClaimantRequests.WithdrawRequest;
import com.claims.platform.claims.intake.application.AppealMyClaim;
import com.claims.platform.claims.intake.application.GetMyClaim;
import com.claims.platform.claims.intake.application.GetMyClaims;
import com.claims.platform.claims.intake.application.SubmitClaim;
import com.claims.platform.claims.intake.application.WithdrawMyClaim;
import com.claims.platform.claims.platform.api.CurrentUser;
import com.claims.platform.common.web.Headers;
import com.claims.platform.web.security.PlatformUser;
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
@RequestMapping("/claimant/v1/claims")
public class ClaimantClaimController {
    private final SubmitClaim submitClaim;
    private final GetMyClaims getMyClaims;
    private final GetMyClaim getMyClaim;
    private final WithdrawMyClaim withdrawMyClaim;
    private final AppealMyClaim appealMyClaim;

    public ClaimantClaimController(SubmitClaim submitClaim, GetMyClaims getMyClaims, GetMyClaim getMyClaim,
                                   WithdrawMyClaim withdrawMyClaim, AppealMyClaim appealMyClaim) {
        this.submitClaim = submitClaim;
        this.getMyClaims = getMyClaims;
        this.getMyClaim = getMyClaim;
        this.withdrawMyClaim = withdrawMyClaim;
        this.appealMyClaim = appealMyClaim;
    }

    /** C3. 201 with the claim number; a replay of the same Idempotency-Key returns the original result. */
    @PostMapping
    public ResponseEntity<SubmitClaim.Result> submit(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestHeader(Headers.IDEMPOTENCY_KEY) UUID idempotencyKey,
                                                     @Valid @RequestBody SubmitClaimRequest body) {
        PlatformUser user = CurrentUser.of(jwt);
        SubmitClaim.Command command = new SubmitClaim.Command(user.id(), user.name(), CurrentUser.homeMarket(jwt),
                Channel.APP, body.policyNumber(), body.lineOfBusiness(), body.incidentAt(),
                body.incident().toDomain(body.lineOfBusiness()),
                body.claimedAmount() == null ? null : body.claimedAmount().toMoney("claimedAmount"),
                body.uploadIds(), body.contact() == null ? null : body.contact().phone(),
                body.contact() == null ? null : body.contact().email());
        SubmitClaim.Result result = submitClaim.submit(CurrentUser.actor(jwt), idempotencyKey, command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/claimant/v1/claims/" + result.claimId()))
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result);
    }

    /** C4. */
    @GetMapping
    public Page<GetMyClaims.Item> list(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(required = false) Integer limit) {
        return getMyClaims.list(jwt.getSubject(), PageRequest.of(cursor, limit));
    }

    /** C5. ETag carries the claim version. */
    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimView> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
        GetMyClaim.Detail detail = getMyClaim.detail(new ClaimId(claimId), jwt.getSubject());
        return ResponseEntity.ok().eTag("\"" + detail.version() + "\"").body(detail.claim());
    }

    /** C6. Customer-visible history only. */
    @GetMapping("/{claimId}/timeline")
    public List<TimelineEntry> timeline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
        return getMyClaim.timeline(new ClaimId(claimId), jwt.getSubject());
    }

    /** C9. */
    @PostMapping("/{claimId}/withdraw")
    public StatusResponse withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
                                   @Valid @RequestBody(required = false) WithdrawRequest body) {
        String reason = body == null ? null : body.reason();
        return new StatusResponse(claimId, withdrawMyClaim.withdraw(new ClaimId(claimId), CurrentUser.actor(jwt), reason));
    }

    /** C10. */
    @PostMapping("/{claimId}/appeal")
    public StatusResponse appeal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
                                 @Valid @RequestBody AppealRequest body) {
        return new StatusResponse(claimId, appealMyClaim.appeal(new ClaimId(claimId), CurrentUser.actor(jwt), body.reason()));
    }

    public record StatusResponse(UUID claimId, String status) {
    }
}
