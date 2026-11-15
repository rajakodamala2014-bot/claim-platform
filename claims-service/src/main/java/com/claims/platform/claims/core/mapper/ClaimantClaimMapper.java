package com.claims.platform.claims.core.mapper;

import com.claims.platform.claims.core.dto.response.TimelineEntryResponse;
import com.claims.platform.claims.core.dto.response.ClaimantInformationRequestResponse;
import com.claims.platform.claims.core.dto.response.ClaimantDocumentResponse;
import com.claims.platform.claims.core.dto.response.ClaimantPaymentResponse;
import com.claims.platform.claims.core.dto.response.ClaimantDecisionResponse;
import com.claims.platform.claims.core.dto.response.ActionNeededResponse;
import com.claims.platform.claims.core.dto.response.ClaimantClaimResponse;
import com.claims.platform.claims.core.dto.response.MoneyResponse;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimEvent;
import com.claims.platform.claims.core.entity.ClaimStatus;
import com.claims.platform.claims.core.entity.Decision;
import com.claims.platform.claims.core.entity.Document;
import com.claims.platform.claims.core.entity.InformationRequest;
import com.claims.platform.claims.core.entity.Settlement;
import com.claims.platform.common.event.EventTypes;
import com.claims.platform.common.money.Money;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the customer app may see about a claim. This is the single place that decides what crosses the
 * claimant boundary: no reserves, assessment notes, internal events, handler ids, cover-decline reasons or
 * payment failure details.
 */
public final class ClaimantClaimMapper {
    private ClaimantClaimMapper() {
    }

    public static ClaimantClaimResponse toClaimResponse(Claim claim, List<Document> documents) {
        ActionNeededResponse action = claim.openInformationRequest()
                .map(r -> new ActionNeededResponse(r.id(), r.items(), r.dueAt()))
                .orElse(null);
        ClaimantDecisionResponse decision = claim.decision()
                .filter(d -> d.status() == Decision.Status.FINAL)
                .map(d -> new ClaimantDecisionResponse(d.outcome() == Decision.Outcome.APPROVE ? "APPROVED" : "REJECTED",
                        d.outcome() == Decision.Outcome.APPROVE ? MoneyResponse.of(d.amount()) : null,
                        d.outcome() == Decision.Outcome.REJECT ? d.reasonCode() : null,
                        d.decidedAt()))
                .orElse(null);
        ClaimantPaymentResponse payment = claim.settlement().map(ClaimantClaimMapper::toPaymentResponse).orElse(null);
        return new ClaimantClaimResponse(claim.id().value(), claim.number().value(), claim.status().name(),
                toStatusLabel(claim.status()), describeNextStep(claim), claim.lineOfBusiness().name(), claim.policyNumber(),
                claim.incidentDate(), claim.submittedAt(), claim.statusChangedAt(), action, decision, payment,
                documents.stream().filter(d -> d.status() == Document.Status.UPLOADED)
                        .map(d -> new ClaimantDocumentResponse(d.id(), d.fileName(), d.contentType(), d.uploadedAt())).toList(),
                claim.status().isWithdrawable(), claim.status() == ClaimStatus.REJECTED);
    }

    public static ClaimantInformationRequestResponse toInformationRequestResponse(InformationRequest r) {
        return new ClaimantInformationRequestResponse(r.id(), r.items(), r.dueAt(), r.requestedAt(), r.status().name(), r.closedAt());
    }

    /** Customer timeline: only CLAIMANT-visible events, with only customer-safe details. */
    public static List<TimelineEntryResponse> toTimeline(List<ClaimEvent> events) {
        return events.stream()
                .filter(e -> e.visibility() == ClaimEvent.Visibility.CLAIMANT)
                .map(e -> new TimelineEntryResponse(e.occurredAt(), e.type(), toTimelineTitle(e.type()), toClaimantVisibleDetails(e)))
                .toList();
    }

    private static ClaimantPaymentResponse toPaymentResponse(Settlement s) {
        boolean paid = s.status() == Settlement.Status.PAID;
        return new ClaimantPaymentResponse(paid ? "PAID" : "PROCESSING", MoneyResponse.of(s.amount()), paid ? s.completedAt() : null,
                paid ? s.paymentRef() : null);
    }

    public static String toStatusLabel(ClaimStatus status) {
        return switch (status) {
            case SUBMITTED -> "Claim received";
            case TRIAGED -> "Waiting for a claims handler";
            case ASSIGNED -> "A claims handler has your claim";
            case UNDER_ASSESSMENT -> "Being assessed";
            case AWAITING_INFO -> "Action needed";
            case DECISION_PENDING -> "Decision being finalised";
            case APPROVED -> "Approved";
            case REJECTED -> "Not approved";
            case SETTLEMENT_IN_PROGRESS -> "Payment in progress";
            case SETTLED -> "Paid";
            case WITHDRAWN -> "Withdrawn";
            case CLOSED -> "Closed";
        };
    }

    static String describeNextStep(Claim claim) {
        return switch (claim.status()) {
            case SUBMITTED -> "We're confirming your policy cover. No action needed.";
            case TRIAGED, ASSIGNED -> "A claims handler will review your claim shortly.";
            case UNDER_ASSESSMENT, DECISION_PENDING -> "We're assessing your claim. We'll contact you if we need anything.";
            case AWAITING_INFO -> "Please send the information we asked for.";
            case APPROVED -> "We'll arrange payment shortly.";
            case SETTLEMENT_IN_PROGRESS -> "Your payment is being processed.";
            case SETTLED -> "Payment completed.";
            case REJECTED -> "You can appeal if you have new information.";
            case WITHDRAWN, CLOSED -> "No further action needed.";
        };
    }

    static String toTimelineTitle(String eventType) {
        return switch (eventType) {
            case EventTypes.CLAIM_SUBMITTED -> "Claim received";
            case EventTypes.CLAIM_TRIAGED -> "Claim queued for review";
            case EventTypes.CLAIM_ASSIGNED -> "Claims handler assigned";
            case EventTypes.ASSESSMENT_STARTED -> "Assessment started";
            case EventTypes.INFORMATION_REQUESTED -> "More information requested";
            case EventTypes.INFORMATION_PROVIDED -> "Information sent";
            case EventTypes.CLAIM_APPROVED -> "Claim approved";
            case EventTypes.CLAIM_REJECTED -> "Claim not approved";
            case EventTypes.SETTLEMENT_INSTRUCTED -> "Payment instructed";
            case EventTypes.PAYMENT_CONFIRMED -> "Payment sent";
            case EventTypes.CLAIM_SETTLED -> "Claim paid";
            case EventTypes.CLAIM_WITHDRAWN -> "Claim withdrawn";
            case EventTypes.CLAIM_REOPENED -> "Appeal received";
            case EventTypes.CLAIM_CLOSED -> "Claim closed";
            default -> eventType;
        };
    }

    private static Map<String, Object> toClaimantVisibleDetails(ClaimEvent e) {
        Map<String, Object> d = e.data();
        return switch (e.type()) {
            case EventTypes.CLAIM_SUBMITTED -> pickEntries(d, "claimNumber", "policyNumber");
            case EventTypes.INFORMATION_REQUESTED -> pickEntries(d, "requestId", "items", "dueAt");
            case EventTypes.INFORMATION_PROVIDED -> pickEntries(d, "requestId");
            case EventTypes.CLAIM_APPROVED -> pickEntries(d, "approvedAmountMinor", "currency");
            case EventTypes.CLAIM_REJECTED -> pickEntries(d, "reasonCode");
            case EventTypes.SETTLEMENT_INSTRUCTED, EventTypes.PAYMENT_CONFIRMED -> pickEntries(d, "amountMinor", "currency");
            case EventTypes.CLAIM_SETTLED -> pickEntries(d, "totalPaidMinor", "currency");
            default -> Map.of();
        };
    }

    private static Map<String, Object> pickEntries(Map<String, Object> data, String... keys) {
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        for (String key : keys) {
            if (data.containsKey(key)) {
                out.put(key, data.get(key));
            }
        }
        return out;
    }
}
