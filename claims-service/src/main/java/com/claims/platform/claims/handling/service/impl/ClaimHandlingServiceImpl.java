package com.claims.platform.claims.handling.service.impl;

import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.entity.ApprovalRequirement;
import com.claims.platform.claims.core.entity.Assessment;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.Decision;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.service.StaffClaimCommandService.Outcome;
import com.claims.platform.claims.core.service.StaffClaimCommandService;
import com.claims.platform.claims.handling.repository.AuthorityLimitRepository;
import com.claims.platform.claims.handling.entity.ReviewAction;
import com.claims.platform.claims.handling.service.ClaimHandlingService;
import com.claims.platform.claims.platform.repository.MarketConfigRepository;
import com.claims.platform.common.money.Money;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ClaimHandlingServiceImpl implements ClaimHandlingService {
    private final StaffClaimCommandService staffCommands;
    private final Clock clock;
    private final MarketConfigRepository markets;
    private final AuthorityLimitRepository authority;

    public ClaimHandlingServiceImpl(StaffClaimCommandService staffCommands, Clock clock, MarketConfigRepository markets,
                                    AuthorityLimitRepository authority) {
        this.staffCommands = staffCommands;
        this.clock = clock;
        this.markets = markets;
        this.authority = authority;
    }

    @Override
    public Outcome<String> assignClaim(ClaimId claimId, StaffContext staff, long expectedVersion, String assigneeId, String teamId) {
        String assignee = assigneeId == null || assigneeId.isBlank() ? staff.actor().id() : assigneeId;
        String team = teamId == null || teamId.isBlank() ? staff.teamId() : teamId;
        return staffCommands.applyChange(claimId, staff, expectedVersion, claim -> {
            claim.assign(assignee, team, staff.actor(), clock.instant());
            return assignee;
        });
    }

    @Override
    public Outcome<Void> startAssessment(ClaimId claimId, StaffContext staff, long expectedVersion) {
        return staffCommands.applyChange(claimId, staff, expectedVersion, claim -> {
            claim.startAssessment(staff.actor(), clock.instant());
            return null;
        });
    }

    @Override
    public Outcome<Money> setReserve(ClaimId claimId, StaffContext staff, long expectedVersion, Money newTotal, String reason) {
        return staffCommands.applyChange(claimId, staff, expectedVersion, claim -> {
            claim.changeReserve(newTotal, reason, staff.actor(), clock.instant());
            return claim.outstandingReserve();
        });
    }

    @Override
    public Outcome<UUID> requestInformation(ClaimId claimId, StaffContext staff, long expectedVersion, List<String> items, Instant dueAt) {
        UUID requestId = UUID.randomUUID();
        return staffCommands.applyChange(claimId, staff, expectedVersion, claim -> {
            Instant now = clock.instant();
            Instant due = dueAt != null ? dueAt
                    : now.plus(Duration.ofDays(markets.requireEnabled(claim.market()).infoRequestDueDays()));
            claim.requestInformation(requestId, items, due, staff.actor(), now);
            return requestId;
        });
    }

    @Override
    public Outcome<UUID> recordAssessment(ClaimId claimId, StaffContext staff, long expectedVersion, Assessment.Liability liability,
                                Money assessedAmount, String notes) {
        UUID assessmentId = UUID.randomUUID();
        return staffCommands.applyChange(claimId, staff, expectedVersion, claim -> {
            claim.recordAssessment(assessmentId, liability, assessedAmount, notes, staff.actor(), clock.instant());
            return assessmentId;
        });
    }

    @Override
    public Outcome<Decision> proposeDecision(ClaimId claimId, StaffContext staff, long expectedVersion, Decision.Outcome outcome,
                                     Money approvedAmount, String reasonCode) {
        UUID decisionId = UUID.randomUUID();
        return staffCommands.applyChange(claimId, staff, expectedVersion, claim -> {
            Money atStake = outcome == Decision.Outcome.APPROVE ? approvedAmount : claim.outstandingReserve();
            ApprovalRequirement approval = authority.loadAuthorityPolicy()
                    .requirementFor(staff.actor(), claim.market(), claim.lineOfBusiness(), atStake);
            return claim.proposeDecision(decisionId, outcome, atStake, reasonCode, approval, staff.actor(), clock.instant());
        });
    }

    @Override
    public Outcome<ReviewAction> reviewDecision(ClaimId claimId, UUID decisionId, StaffContext staff, long expectedVersion,
                                  ReviewAction action, String comment) {
        return staffCommands.applyChange(claimId, staff, expectedVersion, claim -> {
            Decision pending = claim.decision()
                    .filter(d -> d.id().equals(decisionId) && d.status() == Decision.Status.AWAITING_APPROVAL)
                    .orElseThrow(() -> new DomainException(ErrorCode.DECISION_NOT_FOUND,
                            "No decision " + decisionId + " is awaiting approval"));
            if (action == ReviewAction.APPROVE) {
                // the more fundamental rule first: nobody approves their own proposal, whatever their limit
                if (pending.proposedBy().equals(staff.actor().id())) {
                    throw new DomainException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED, "You cannot approve your own proposal");
                }
                authority.loadAuthorityPolicy().requireCanApprove(staff.actor(), claim.market(), claim.lineOfBusiness(),
                        pending.amount());
                claim.approveDecision(decisionId, staff.actor(), clock.instant());
            } else {
                claim.returnDecision(decisionId, comment, staff.actor(), clock.instant());
            }
            return action;
        });
    }
}
