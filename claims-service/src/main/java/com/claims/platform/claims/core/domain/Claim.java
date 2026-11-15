package com.claims.platform.claims.core.domain;

import com.claims.platform.common.event.EventTypes;
import com.claims.platform.common.money.Money;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.claims.platform.claims.core.domain.ClaimEvent.Visibility.CLAIMANT;
import static com.claims.platform.claims.core.domain.ClaimEvent.Visibility.INTERNAL;

/**
 * Aggregate root. Owns the state machine ({@link ClaimStatus}), the reserve ledger, information requests,
 * assessment, decision and settlement. Every command:
 * <ol>
 *     <li>checks who may do it (owner / assigned handler / supervisor),</li>
 *     <li>checks the transition is allowed <em>before</em> changing anything,</li>
 *     <li>records one or more {@link ClaimEvent}s, which the application layer persists together with the
 *     claim (claim_event + outbox) in one transaction.</li>
 * </ol>
 * There is no status setter: the only write to {@code status} is {@link #transitionTo}.
 * Authority limits are evaluated outside (handling module's AuthorityPolicy) and passed in as an
 * {@link ApprovalRequirement}; who may <em>see</em> a claim (markets) is an application concern.
 */
public final class Claim {
    private final ClaimId id;
    private final ClaimNumber number;
    private final MarketCode market;
    private final LineOfBusiness lineOfBusiness;
    private final String policyNumber;
    private final String claimantId;
    private final Channel channel;
    private final String currency;
    private final Instant incidentDate;
    private final Money claimedAmount;
    private final Instant submittedAt;
    private final ReserveLedger reserves;
    private final List<InformationRequest> informationRequests = new ArrayList<>();
    private final List<ClaimEvent> pendingEvents = new ArrayList<>();

    private ClaimStatus status;
    private Instant statusChangedAt;
    private CoverStatus coverStatus;
    private String coverDeclineReason;
    private String handlerId;
    private String teamId;
    private Instant slaDueAt;
    private Instant slaPausedAt;
    private Assessment assessment;
    private Decision decision;
    private Money approvedAmount;
    private Settlement settlement;
    private long version;

    private Claim(NewClaim claim, Instant now) {
        this.id = claim.id();
        this.number = claim.number();
        this.market = claim.market();
        this.lineOfBusiness = claim.lineOfBusiness();
        this.policyNumber = claim.policyNumber();
        this.claimantId = claim.claimantId();
        this.channel = claim.channel();
        this.currency = Money.zero(claim.currency()).currency();
        this.incidentDate = claim.incidentDate();
        this.claimedAmount = claim.claimedAmount();
        this.submittedAt = now;
        this.reserves = ReserveLedger.empty(currency);
        this.status = ClaimStatus.SUBMITTED;
        this.statusChangedAt = now;
        this.coverStatus = CoverStatus.PENDING;
        this.slaDueAt = now.plus(claim.slaTarget());
    }

    private Claim(ClaimState s) {
        this.id = s.id();
        this.number = s.number();
        this.market = s.market();
        this.lineOfBusiness = s.lineOfBusiness();
        this.policyNumber = s.policyNumber();
        this.claimantId = s.claimantId();
        this.channel = s.channel();
        this.currency = Money.zero(s.currency()).currency();
        this.incidentDate = s.incidentDate();
        this.claimedAmount = s.claimedAmount();
        this.submittedAt = s.submittedAt();
        this.reserves = ReserveLedger.restore(currency, s.reserveMovements());
        this.informationRequests.addAll(s.informationRequests());
        this.status = s.status();
        this.statusChangedAt = s.statusChangedAt();
        this.coverStatus = s.coverStatus();
        this.coverDeclineReason = s.coverDeclineReason();
        this.handlerId = s.handlerId();
        this.teamId = s.teamId();
        this.slaDueAt = s.slaDueAt();
        this.slaPausedAt = s.slaPausedAt();
        this.assessment = s.assessment();
        this.decision = s.decision();
        this.approvedAmount = s.approvedAmount();
        this.settlement = s.settlement();
        this.version = s.version();
    }

    /** Rebuilds a claim from storage. Repository use only; no events are recorded. */
    public static Claim restore(ClaimState state) {
        return new Claim(state);
    }

    /** Called by the repository after a successful write with the new optimistic-lock version. */
    public void markSaved(long newVersion) {
        if (newVersion <= version) {
            throw new IllegalArgumentException("Version must increase: " + version + " -> " + newVersion);
        }
        this.version = newVersion;
    }

    // ------------------------------------------------------------------ intake

    /** C3 (customer, channel APP) or S1 (staff on the customer's behalf, channel STAFF). Cover check pending. */
    public static Claim submit(NewClaim newClaim, Actor actor, Instant now) {
        Objects.requireNonNull(newClaim, "newClaim");
        if (newClaim.channel() == Channel.APP
                && !(actor.role() == Role.CLAIMANT && actor.id().equals(newClaim.claimantId()))) {
            throw new DomainException(ErrorCode.NOT_CLAIM_OWNER, "Customers can only report claims for themselves");
        }
        if (newClaim.channel() == Channel.STAFF && !actor.role().isStaff()) {
            throw new DomainException(ErrorCode.FORBIDDEN_ROLE, "Only staff can register a claim on a customer's behalf");
        }
        Claim claim = new Claim(newClaim, now);
        claim.record(EventTypes.CLAIM_SUBMITTED, CLAIMANT, actor, now, data(
                "claimNumber", claim.number.value(),
                "market", claim.market.value(),
                "lineOfBusiness", claim.lineOfBusiness.name(),
                "policyNumber", claim.policyNumber,
                "claimantId", claim.claimantId,
                "channel", claim.channel.name(),
                "incidentDate", claim.incidentDate.toString(),
                "claimedAmountMinor", claim.claimedAmount == null ? null : claim.claimedAmount.amountMinor(),
                "currency", claim.currency,
                "slaDueAt", claim.slaDueAt.toString()));
        return claim;
    }

    /** Policy system confirmed cover (inline at submission or by the retry job). */
    public void confirmCover(Actor actor, Instant now) {
        transitionTo(ClaimStatus.TRIAGED, now);
        coverStatus = CoverStatus.CONFIRMED;
        record(EventTypes.CLAIM_TRIAGED, CLAIMANT, actor, now, data("coverStatus", coverStatus.name()));
    }

    /**
     * Policy system says not covered. The claim is still triaged, flagged DECLINED, so it reaches a handler
     * who reviews it (normally rejecting it) — the customer always gets a human decision.
     */
    public void declineCover(String reason, Actor actor, Instant now) {
        requireText(reason, "A cover decline reason is required");
        transitionTo(ClaimStatus.TRIAGED, now);
        coverStatus = CoverStatus.DECLINED;
        coverDeclineReason = reason;
        record(EventTypes.COVER_DECLINED, INTERNAL, actor, now, data("reason", reason));
        record(EventTypes.CLAIM_TRIAGED, CLAIMANT, actor, now, data("coverStatus", coverStatus.name()));
    }

    // ------------------------------------------------------------------ handling

    /**
     * S4. A handler picks up an unassigned TRIAGED claim for themselves; a supervisor may assign it to anyone
     * or reassign it later. A handler hitting an already-assigned claim gets CLAIM_ALREADY_ASSIGNED (409).
     */
    public void assign(String assigneeId, String assigneeTeamId, Actor actor, Instant now) {
        requireText(assigneeId, "Assignee is required");
        requireStaff(actor);
        if (actor.role() == Role.HANDLER && !actor.id().equals(assigneeId)) {
            throw new DomainException(ErrorCode.FORBIDDEN_ROLE, "Handlers can only pick up claims for themselves");
        }
        if (handlerId != null) {
            if (!actor.role().isSupervisor()) {
                throw new DomainException(ErrorCode.CLAIM_ALREADY_ASSIGNED, "Claim " + number + " is already assigned");
            }
            if (status.isOutcomeReached()) {
                throw new DomainException(ErrorCode.CLAIM_INVALID_TRANSITION, "Claim in " + status + " cannot be reassigned");
            }
            if (handlerId.equals(assigneeId)) {
                return;
            }
            String previous = handlerId;
            handlerId = assigneeId;
            teamId = assigneeTeamId;
            record(EventTypes.CLAIM_ASSIGNED, CLAIMANT, actor, now,
                    data("handlerId", handlerId, "teamId", teamId, "previousHandlerId", previous));
            return;
        }
        transitionTo(ClaimStatus.ASSIGNED, now);
        handlerId = assigneeId;
        teamId = assigneeTeamId;
        record(EventTypes.CLAIM_ASSIGNED, CLAIMANT, actor, now, data("handlerId", handlerId, "teamId", teamId));
    }

    /** S5. */
    public void startAssessment(Actor actor, Instant now) {
        requireHandlerOrSupervisor(actor);
        requireSource(ClaimStatus.ASSIGNED, ClaimStatus.UNDER_ASSESSMENT);
        transitionTo(ClaimStatus.UNDER_ASSESSMENT, now);
        record(EventTypes.ASSESSMENT_STARTED, CLAIMANT, actor, now, data("handlerId", handlerId));
    }

    /** S6. Sets the reserve to {@code newTotal}; the ledger records the difference. No status change. */
    public void changeReserve(Money newTotal, String reason, Actor actor, Instant now) {
        requireHandlerOrSupervisor(actor);
        if (handlerId == null || status.isOutcomeReached()) {
            throw new DomainException(ErrorCode.CLAIM_INVALID_TRANSITION, "Reserve cannot be changed in status " + status);
        }
        reserves.adjustTo(newTotal, reason, actor.id(), now)
                .ifPresent(movement -> recordReserveChanged(movement, actor, now));
    }

    /** S7. Pauses the SLA clock: the company is waiting on the customer. */
    public InformationRequest requestInformation(UUID requestId, List<String> items, Instant dueAt, Actor actor, Instant now) {
        requireHandlerOrSupervisor(actor);
        if (items == null || items.isEmpty() || items.stream().anyMatch(item -> item == null || item.isBlank())) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "At least one requested item is required");
        }
        if (dueAt == null || !dueAt.isAfter(now)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Due date must be in the future");
        }
        transitionTo(ClaimStatus.AWAITING_INFO, now);
        InformationRequest request = new InformationRequest(requestId, items, dueAt, actor.id(), now,
                InformationRequest.Status.OPEN, null, List.of(), null);
        informationRequests.add(request);
        pauseSla(now);
        record(EventTypes.INFORMATION_REQUESTED, CLAIMANT, actor, now,
                data("requestId", requestId.toString(), "items", List.copyOf(items), "dueAt", dueAt.toString(),
                        "handlerId", handlerId));
        return request;
    }

    /** C8. The customer answers; the claim goes back to the handler and the SLA clock resumes. */
    public void provideInformation(UUID requestId, String answer, List<UUID> documentIds, Actor actor, Instant now) {
        requireOwner(actor);
        InformationRequest open = openRequest(requestId);
        requireSource(ClaimStatus.AWAITING_INFO, ClaimStatus.UNDER_ASSESSMENT);
        replaceRequest(open.answered(answer, documentIds, now));
        transitionTo(ClaimStatus.UNDER_ASSESSMENT, now);
        resumeSla(now);
        record(EventTypes.INFORMATION_PROVIDED, CLAIMANT, actor, now, data("requestId", requestId.toString(),
                "documentIds", documentIds == null ? List.of() : documentIds.stream().map(UUID::toString).toList(),
                "slaDueAt", slaDueAt.toString(), "handlerId", handlerId));
    }

    /** Info-request expiry job: the due date passed without an answer; back to the handler. */
    public void expireInformationRequest(UUID requestId, Instant now) {
        InformationRequest open = openRequest(requestId);
        if (open.dueAt().isAfter(now)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Information request " + requestId + " is not due yet");
        }
        requireSource(ClaimStatus.AWAITING_INFO, ClaimStatus.UNDER_ASSESSMENT);
        replaceRequest(open.expired(now));
        transitionTo(ClaimStatus.UNDER_ASSESSMENT, now);
        resumeSla(now);
        record(EventTypes.INFORMATION_REQUEST_EXPIRED, INTERNAL, Actor.SYSTEM, now, data("requestId", requestId.toString(),
                "slaDueAt", slaDueAt.toString(), "handlerId", handlerId));
    }

    /** S8. Fault finding, assessed amount and notes. No status change. */
    public void recordAssessment(UUID assessmentId, Assessment.Liability liability, Money assessedAmount, String notes,
                                 Actor actor, Instant now) {
        requireHandlerOrSupervisor(actor);
        if (status != ClaimStatus.UNDER_ASSESSMENT) {
            throw new DomainException(ErrorCode.CLAIM_INVALID_TRANSITION,
                    "An assessment can only be recorded while UNDER_ASSESSMENT, not " + status);
        }
        requireClaimCurrency(assessedAmount);
        assessment = new Assessment(assessmentId, liability, assessedAmount, notes, actor.id(), now);
        record(EventTypes.ASSESSMENT_RECORDED, INTERNAL, actor, now, data("assessmentId", assessmentId.toString(),
                "liability", liability.name(), "assessedAmountMinor", assessedAmount.amountMinor(), "currency", currency));
    }

    /**
     * S9. Moves to DECISION_PENDING. If {@code approval} is NONE (within the proposer's authority) the decision
     * is final at once and the claim continues to APPROVED/REJECTED in the same command.
     *
     * @param amount approved amount (APPROVE) or amount at stake (REJECT), in the claim currency
     */
    public Decision proposeDecision(UUID decisionId, Decision.Outcome outcome, Money amount, String reasonCode,
                                    ApprovalRequirement approval, Actor actor, Instant now) {
        requireHandlerOrSupervisor(actor);
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(approval, "approval");
        status.requireTransitionTo(ClaimStatus.DECISION_PENDING);
        requireClaimCurrency(amount);
        if (outcome == Decision.Outcome.APPROVE && !amount.isPositive()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "An approval needs a positive amount");
        }
        if (outcome == Decision.Outcome.REJECT) {
            requireText(reasonCode, "A rejection needs a reason");
            if (amount.isNegative()) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "Amount cannot be negative");
            }
        }
        decision = new Decision(decisionId, outcome, amount, reasonCode, actor.id(), now, approval,
                Decision.Status.AWAITING_APPROVAL, null, null);
        transitionTo(ClaimStatus.DECISION_PENDING, now);
        record(EventTypes.DECISION_PROPOSED, INTERNAL, actor, now, data(
                "decisionId", decisionId.toString(),
                "outcome", outcome.name(),
                "amountMinor", amount.amountMinor(),
                "currency", currency,
                "reasonCode", reasonCode,
                "approvalRequired", approval == ApprovalRequirement.SUPERVISOR_APPROVAL));
        if (approval == ApprovalRequirement.NONE) {
            applyFinalDecision(actor, now);
        }
        return decision;
    }

    /** S10 approve. The approver's own limit is checked by the application (AuthorityPolicy) before this. */
    public void approveDecision(UUID decisionId, Actor actor, Instant now) {
        requireSupervisor(actor);
        Decision pending = awaitingApproval(decisionId);
        if (pending.proposedBy().equals(actor.id())) {
            throw new DomainException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED, "You cannot approve your own proposal");
        }
        status.requireTransitionTo(targetOf(pending));
        record(EventTypes.DECISION_APPROVED, INTERNAL, actor, now,
                data("decisionId", decisionId.toString(), "approverId", actor.id(), "proposerId", pending.proposedBy()));
        applyFinalDecision(actor, now);
    }

    /** S10 return: the supervisor sends the proposal back; the claim returns to UNDER_ASSESSMENT. */
    public void returnDecision(UUID decisionId, String comment, Actor actor, Instant now) {
        requireSupervisor(actor);
        Decision pending = awaitingApproval(decisionId);
        requireText(comment, "Say why the decision is returned");
        requireSource(ClaimStatus.DECISION_PENDING, ClaimStatus.UNDER_ASSESSMENT);
        transitionTo(ClaimStatus.UNDER_ASSESSMENT, now);
        decision = pending.returned(actor.id(), now);
        record(EventTypes.DECISION_RETURNED, INTERNAL, actor, now, data("decisionId", decisionId.toString(),
                "approverId", actor.id(), "proposerId", pending.proposedBy(), "comment", comment));
    }

    // ------------------------------------------------------------------ settlement

    /** S11. Amount may not exceed what was approved. */
    public Settlement instructSettlement(UUID settlementId, UUID instructionId, Payee payee, Money amount,
                                        Actor actor, Instant now) {
        requireHandlerOrSupervisor(actor);
        status.requireTransitionTo(ClaimStatus.SETTLEMENT_IN_PROGRESS);
        requireClaimCurrency(amount);
        if (!amount.isPositive()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Settlement amount must be positive");
        }
        if (amount.isGreaterThan(approvedAmount)) {
            throw new DomainException(ErrorCode.SETTLEMENT_EXCEEDS_APPROVED,
                    "Settlement " + amount + " exceeds the approved " + approvedAmount);
        }
        settlement = new Settlement(settlementId, amount, payee, instructionId, 1, Settlement.Status.INSTRUCTED,
                null, null, now, null);
        transitionTo(ClaimStatus.SETTLEMENT_IN_PROGRESS, now);
        recordSettlementInstructed(CLAIMANT, actor, now);
        return settlement;
    }

    /** S12. Retry a FAILED payment, optionally with corrected bank details, as a new instruction. */
    public Settlement retrySettlement(UUID settlementId, UUID newInstructionId, Payee correctedPayee, Actor actor, Instant now) {
        requireHandlerOrSupervisor(actor);
        if (status != ClaimStatus.SETTLEMENT_IN_PROGRESS) {
            throw new DomainException(ErrorCode.CLAIM_INVALID_TRANSITION, "No settlement in progress (status " + status + ")");
        }
        Settlement current = currentSettlement(settlementId);
        if (current.status() != Settlement.Status.FAILED) {
            throw new DomainException(ErrorCode.SETTLEMENT_NOT_RETRYABLE, "Only a failed payment can be retried");
        }
        settlement = current.retried(newInstructionId, correctedPayee == null ? current.payee() : correctedPayee, now);
        recordSettlementInstructed(INTERNAL, actor, now);
        return settlement;
    }

    /**
     * Payment result consumer: PAID. Settles the claim and releases the reserve.
     *
     * @return false if the result was a duplicate or belongs to a superseded attempt (ignored)
     */
    public boolean confirmPayment(UUID settlementId, UUID instructionId, String paymentRef, Instant paidAt, Instant now) {
        Settlement current = currentSettlement(settlementId);
        if (!current.instructionId().equals(instructionId) || current.status() != Settlement.Status.INSTRUCTED) {
            return false;
        }
        status.requireTransitionTo(ClaimStatus.SETTLED);
        settlement = current.paid(paymentRef, paidAt);
        record(EventTypes.PAYMENT_CONFIRMED, CLAIMANT, Actor.SYSTEM, now, data("settlementId", settlementId.toString(),
                "paymentRef", paymentRef, "paidAt", paidAt.toString(),
                "amountMinor", current.amount().amountMinor(), "currency", currency));
        transitionTo(ClaimStatus.SETTLED, now);
        record(EventTypes.CLAIM_SETTLED, CLAIMANT, Actor.SYSTEM, now,
                data("totalPaidMinor", current.amount().amountMinor(), "currency", currency));
        releaseReserve("Claim settled", Actor.SYSTEM, now);
        return true;
    }

    /**
     * Payment result consumer: FAILED. Status stays SETTLEMENT_IN_PROGRESS; the handler gets a task to retry.
     *
     * @return false if duplicate/superseded (ignored)
     */
    public boolean failPayment(UUID settlementId, UUID instructionId, String reason, Instant now) {
        Settlement current = currentSettlement(settlementId);
        if (!current.instructionId().equals(instructionId) || current.status() != Settlement.Status.INSTRUCTED) {
            return false;
        }
        settlement = current.failed(reason, now);
        record(EventTypes.PAYMENT_FAILED, INTERNAL, Actor.SYSTEM, now,
                data("settlementId", settlementId.toString(), "reason", reason, "handlerId", handlerId));
        return true;
    }

    // ------------------------------------------------------------------ customer lifecycle + closure

    /** C9. Only before a decision has been proposed. */
    public void withdraw(String reason, Actor actor, Instant now) {
        requireOwner(actor);
        transitionTo(ClaimStatus.WITHDRAWN, now);
        informationRequests.replaceAll(r -> r.status() == InformationRequest.Status.OPEN ? r.cancelled(now) : r);
        slaPausedAt = null;
        record(EventTypes.CLAIM_WITHDRAWN, CLAIMANT, actor, now, data("reason", reason, "handlerId", handlerId));
        releaseReserve("Claim withdrawn", actor, now);
    }

    /** C10. Customer appeals a rejection: reopened for assessment by the same handler. */
    public void appeal(String reason, Actor actor, Instant now) {
        requireOwner(actor);
        requireText(reason, "Please tell us why you are appealing");
        requireSource(ClaimStatus.REJECTED, ClaimStatus.UNDER_ASSESSMENT);
        transitionTo(ClaimStatus.UNDER_ASSESSMENT, now);
        decision = decision == null ? null : decision.superseded();
        record(EventTypes.CLAIM_REOPENED, CLAIMANT, actor, now, data("reason", reason, "handlerId", handlerId));
    }

    /** Auto-close job: SETTLED / REJECTED / WITHDRAWN claims whose cooling-off period has elapsed. */
    public boolean isDueForClosure(Instant now, Duration coolingOff) {
        return status.isAutoClosable() && !statusChangedAt.plus(coolingOff).isAfter(now);
    }

    public void close(Instant now) {
        ClaimStatus previous = status;
        transitionTo(ClaimStatus.CLOSED, now);
        releaseReserve("Claim closed", Actor.SYSTEM, now);
        record(EventTypes.CLAIM_CLOSED, CLAIMANT, Actor.SYSTEM, now,
                data("closedAt", now.toString(), "previousStatus", previous.name()));
    }

    // ------------------------------------------------------------------ events

    /** Events recorded since the last call; the application persists them with the claim, then calls this. */
    public List<ClaimEvent> pullPendingEvents() {
        List<ClaimEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    public List<ClaimEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    // ------------------------------------------------------------------ queries

    public ClaimId id() {
        return id;
    }

    public ClaimNumber number() {
        return number;
    }

    public MarketCode market() {
        return market;
    }

    public LineOfBusiness lineOfBusiness() {
        return lineOfBusiness;
    }

    public String policyNumber() {
        return policyNumber;
    }

    public String claimantId() {
        return claimantId;
    }

    public Channel channel() {
        return channel;
    }

    public String currency() {
        return currency;
    }

    public Instant incidentDate() {
        return incidentDate;
    }

    public Optional<Money> claimedAmount() {
        return Optional.ofNullable(claimedAmount);
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public ClaimStatus status() {
        return status;
    }

    public Instant statusChangedAt() {
        return statusChangedAt;
    }

    public CoverStatus coverStatus() {
        return coverStatus;
    }

    public Optional<String> coverDeclineReason() {
        return Optional.ofNullable(coverDeclineReason);
    }

    public Optional<String> handlerId() {
        return Optional.ofNullable(handlerId);
    }

    public Optional<String> teamId() {
        return Optional.ofNullable(teamId);
    }

    public Instant slaDueAt() {
        return slaDueAt;
    }

    public boolean isSlaPaused() {
        return slaPausedAt != null;
    }

    public Optional<Instant> slaPausedAt() {
        return Optional.ofNullable(slaPausedAt);
    }

    public Money outstandingReserve() {
        return reserves.outstanding();
    }

    public List<ReserveMovement> reserveMovements() {
        return reserves.movements();
    }

    public List<InformationRequest> informationRequests() {
        return List.copyOf(informationRequests);
    }

    public Optional<InformationRequest> openInformationRequest() {
        return informationRequests.stream().filter(r -> r.status() == InformationRequest.Status.OPEN).findFirst();
    }

    public Optional<Assessment> assessment() {
        return Optional.ofNullable(assessment);
    }

    public Optional<Decision> decision() {
        return Optional.ofNullable(decision);
    }

    public Optional<Money> approvedAmount() {
        return Optional.ofNullable(approvedAmount);
    }

    public Optional<Settlement> settlement() {
        return Optional.ofNullable(settlement);
    }

    /** Optimistic-locking version, maintained by persistence; exposed as the ETag. */
    public long version() {
        return version;
    }

    // ------------------------------------------------------------------ internals

    private void transitionTo(ClaimStatus target, Instant now) {
        status.requireTransitionTo(target);
        status = target;
        statusChangedAt = now;
    }

    /**
     * For commands whose target is reachable from several statuses (e.g. UNDER_ASSESSMENT), the table alone
     * is not enough: "start assessment" must come from ASSIGNED and "appeal" from REJECTED.
     */
    private void requireSource(ClaimStatus expected, ClaimStatus target) {
        if (status != expected) {
            throw new InvalidTransitionException(status, target);
        }
    }

    private void applyFinalDecision(Actor actor, Instant now) {
        decision = decision.finalise(actor.id(), now);
        if (decision.outcome() == Decision.Outcome.APPROVE) {
            transitionTo(ClaimStatus.APPROVED, now);
            approvedAmount = decision.amount();
            record(EventTypes.CLAIM_APPROVED, CLAIMANT, actor, now, data("decisionId", decision.id().toString(),
                    "approvedAmountMinor", approvedAmount.amountMinor(), "currency", currency));
        } else {
            transitionTo(ClaimStatus.REJECTED, now);
            record(EventTypes.CLAIM_REJECTED, CLAIMANT, actor, now,
                    data("decisionId", decision.id().toString(), "reasonCode", decision.reasonCode()));
            releaseReserve("Claim rejected", actor, now);
        }
    }

    private static ClaimStatus targetOf(Decision decision) {
        return decision.outcome() == Decision.Outcome.APPROVE ? ClaimStatus.APPROVED : ClaimStatus.REJECTED;
    }

    private Decision awaitingApproval(UUID decisionId) {
        if (decision == null || !decision.id().equals(decisionId)
                || decision.status() != Decision.Status.AWAITING_APPROVAL) {
            throw new DomainException(ErrorCode.DECISION_NOT_FOUND, "No decision " + decisionId + " is awaiting approval");
        }
        return decision;
    }

    private Settlement currentSettlement(UUID settlementId) {
        if (settlement == null || !settlement.id().equals(settlementId)) {
            throw new DomainException(ErrorCode.SETTLEMENT_NOT_FOUND, "Settlement " + settlementId + " not found");
        }
        return settlement;
    }

    private InformationRequest openRequest(UUID requestId) {
        return informationRequests.stream()
                .filter(r -> r.id().equals(requestId) && r.status() == InformationRequest.Status.OPEN)
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.INFO_REQUEST_NOT_FOUND,
                        "No open information request " + requestId));
    }

    private void replaceRequest(InformationRequest updated) {
        informationRequests.replaceAll(r -> r.id().equals(updated.id()) ? updated : r);
    }

    private void releaseReserve(String reason, Actor actor, Instant now) {
        reserves.release(reason, actor.id(), now).ifPresent(movement -> recordReserveChanged(movement, actor, now));
    }

    private void recordReserveChanged(ReserveMovement movement, Actor actor, Instant now) {
        record(EventTypes.RESERVE_CHANGED, INTERNAL, actor, now, data(
                "movementId", movement.id().toString(),
                "movementType", movement.type().name(),
                "movementMinor", movement.amount().amountMinor(),
                "newOutstandingMinor", movement.balanceAfter().amountMinor(),
                "currency", currency,
                "reason", movement.reason()));
    }

    private void recordSettlementInstructed(ClaimEvent.Visibility visibility, Actor actor, Instant now) {
        record(EventTypes.SETTLEMENT_INSTRUCTED, visibility, actor, now, data(
                "settlementId", settlement.id().toString(),
                "instructionId", settlement.instructionId().toString(),
                "attempt", settlement.attempts(),
                "amountMinor", settlement.amount().amountMinor(),
                "currency", currency));
    }

    private void pauseSla(Instant now) {
        if (slaPausedAt == null) {
            slaPausedAt = now;
        }
    }

    private void resumeSla(Instant now) {
        if (slaPausedAt != null) {
            Duration paused = Duration.between(slaPausedAt, now);
            if (!paused.isNegative()) {
                slaDueAt = slaDueAt.plus(paused);
            }
            slaPausedAt = null;
        }
    }

    private void requireOwner(Actor actor) {
        if (actor.role() != Role.CLAIMANT || !actor.id().equals(claimantId)) {
            throw new DomainException(ErrorCode.NOT_CLAIM_OWNER, "Only the claimant can do this");
        }
    }

    private static void requireStaff(Actor actor) {
        if (!actor.role().isStaff()) {
            throw new DomainException(ErrorCode.FORBIDDEN_ROLE, "Staff role required");
        }
    }

    private static void requireSupervisor(Actor actor) {
        if (!actor.role().isSupervisor()) {
            throw new DomainException(ErrorCode.FORBIDDEN_ROLE, "Team lead or manager role required");
        }
    }

    /** The assigned handler, or any supervisor (who can step in for an absent handler). */
    private void requireHandlerOrSupervisor(Actor actor) {
        requireStaff(actor);
        if (actor.role() == Role.HANDLER && !actor.id().equals(handlerId)) {
            throw new DomainException(ErrorCode.NOT_ASSIGNED_HANDLER, "Only the assigned handler can do this");
        }
    }

    private void requireClaimCurrency(Money amount) {
        Objects.requireNonNull(amount, "amount");
        if (!currency.equals(amount.currency())) {
            throw new DomainException(ErrorCode.CURRENCY_MISMATCH, "Amounts on this claim must be in " + currency);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, message);
        }
    }

    private void record(String type, ClaimEvent.Visibility visibility, Actor actor, Instant now, Map<String, Object> data) {
        pendingEvents.add(new ClaimEvent(UUID.randomUUID(), type, visibility, actor.id(), actor.role(), now, data));
    }

    private static Map<String, Object> data(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
