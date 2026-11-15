package com.claims.platform.claims.handling.api;

import com.claims.platform.claims.core.application.ClaimantViews.InfoRequestView;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.handling.application.MyInformationRequests;
import com.claims.platform.claims.handling.application.RespondToInformationRequest;
import com.claims.platform.claims.platform.api.CurrentUser;
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
@RequestMapping("/claimant/v1/claims/{claimId}/info-requests")
public class ClaimantInformationRequestController {
    private final MyInformationRequests myInformationRequests;
    private final RespondToInformationRequest respond;

    public ClaimantInformationRequestController(MyInformationRequests myInformationRequests,
                                                RespondToInformationRequest respond) {
        this.myInformationRequests = myInformationRequests;
        this.respond = respond;
    }

    public record InformationResponseRequest(@Size(max = 4000) String answer, @Size(max = 20) List<UUID> uploadIds) {
    }

    public record InfoRequestList(List<InfoRequestView> items) {
    }

    public record ResponseResult(UUID claimId, UUID requestId, String status) {
    }

    @GetMapping
    public InfoRequestList list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
        return new InfoRequestList(myInformationRequests.list(new ClaimId(claimId), jwt.getSubject()));
    }

    @PostMapping("/{requestId}/responses")
    public ResponseResult respond(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
                                  @PathVariable UUID requestId, @Valid @RequestBody InformationResponseRequest body) {
        String status = respond.respond(new ClaimId(claimId), requestId, CurrentUser.actor(jwt), body.answer(),
                body.uploadIds());
        return new ResponseResult(claimId, requestId, status);
    }
}
