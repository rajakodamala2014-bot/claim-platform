package com.claims.platform.claims.core.application;

import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimEvent;
import com.claims.platform.claims.core.domain.ClaimStatus;
import com.claims.platform.claims.core.domain.Decision;
import com.claims.platform.claims.core.domain.Document;
import com.claims.platform.claims.core.domain.InformationRequest;
import com.claims.platform.claims.core.domain.Settlement;
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
public final class ClaimantViews {
    private ClaimantViews() {
    }

    public record MoneyView(String amount, long amountMinor, String currency) {
        public static MoneyView of(Money money) {
            return money == null ? null : new MoneyView(money.toMajor().toPlainString(), money.amountMinor(), money.currency());
        }
    }

    public record ClaimView(
            UUID claimId,
            String claimNumber,
            String status,
            String statusLabel,
            String nextStep,
            String lineOfBusiness,
            String policyNumber,
            Instant incidentAt,
            Instant submittedAt,
            Instant lastUpdatedAt,
            ActionNeeded actionNeeded,
            DecisionView decision,
            PaymentView payment,
            List<DocumentView> documents,
            boolean canWithdraw,
            boolean canAppeal
    ) {
    }

    public record ActionNeeded(UUID requestId, List<String> items, Instant dueAt) {
    }

    public record DecisionView(String outcome, MoneyView approvedAmount, String reasonCode, Instant decidedAt) {
    }

    /** {@code status}: PROCESSING (instructed, or failed and being fixed by us) or PAID. */
    public record PaymentView(String status, MoneyView amount, Instant paidAt, String paymentRef) {
    }

    public record DocumentView(UUID documentId, String fileName, String contentType, Instant uploadedAt) {
    }

    public record InfoRequestView(UUID requestId, List<String> items, Instant dueAt, Instant requestedAt,
                                  String status, Instant answeredAt) {
    }

    public record TimelineEntry(Instant at, String type, String title, Map<String, Object> details) {
    }

    public static ClaimView claim(Claim claim, List<Document> documents) {
        ActionNeeded action = claim.openInformationRequest()
                .map(r -> new ActionNeeded(r.id(), r.items(), r.dueAt()))
                .orElse(null);
        DecisionView decision = claim.decision()
                .filter(d -> d.status() == Decision.Status.FINAL)
                .map(d -> new DecisionView(d.outcome() == Decision.Outcome.APPROVE ? "APPROVED" : "REJECTED",
                        d.outcome() == Decision.Outcome.APPROVE ? MoneyView.of(d.amount()) : null,
                        d.outcome() == Decision.Outcome.REJECT ? d.reasonCode() : null,
                        d.decidedAt()))
                .orElse(null);
        PaymentView payment = claim.settlement().map(ClaimantViews::payment).orElse(null);
        return new ClaimView(claim.id().value(), claim.number().value(), claim.status().name(),
                statusLabel(claim.status()), nextStep(claim), claim.lineOfBusiness().name(), claim.policyNumber(),
                claim.incidentDate(), claim.submittedAt(), claim.statusChangedAt(), action, decision, payment,
                documents.stream().filter(d -> d.status() == Document.Status.UPLOADED)
                        .map(d -> new DocumentView(d.id(), d.fileName(), d.contentType(), d.uploadedAt())).toList(),
                claim.status().isWithdrawable(), claim.status() == ClaimStatus.REJECTED);
    }

    public static InfoRequestView infoRequest(InformationRequest r) {
        return new InfoRequestView(r.id(), r.items(), r.dueAt(), r.requestedAt(), r.status().name(), r.closedAt());
    }

    /** Customer timeline: only CLAIMANT-visible events, with only customer-safe details. */
    public static List<TimelineEntry> timeline(List<ClaimEvent> events) {
        return events.stream()
                .filter(e -> e.visibility() == ClaimEvent.Visibility.CLAIMANT)
                .map(e -> new TimelineEntry(e.occurredAt(), e.type(), title(e.type()), safeDetails(e)))
                .toList();
    }

    private static PaymentView payment(Settlement s) {
        boolean paid = s.status() == Settlement.Status.PAID;
        return new PaymentView(paid ? "PAID" : "PROCESSING", MoneyView.of(s.amount()), paid ? s.completedAt() : null,
                paid ? s.paymentRef() : null);
    }

    public static String statusLabel(ClaimStatus status) {
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

    static String nextStep(Claim claim) {
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

    static String title(String eventType) {
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

    private static Map<String, Object> safeDetails(ClaimEvent e) {
        Map<String, Object> d = e.data();
        return switch (e.type()) {
            case EventTypes.CLAIM_SUBMITTED -> pick(d, "claimNumber", "policyNumber");
            case EventTypes.INFORMATION_REQUESTED -> pick(d, "requestId", "items", "dueAt");
            case EventTypes.INFORMATION_PROVIDED -> pick(d, "requestId");
            case EventTypes.CLAIM_APPROVED -> pick(d, "approvedAmountMinor", "currency");
            case EventTypes.CLAIM_REJECTED -> pick(d, "reasonCode");
            case EventTypes.SETTLEMENT_INSTRUCTED, EventTypes.PAYMENT_CONFIRMED -> pick(d, "amountMinor", "currency");
            case EventTypes.CLAIM_SETTLED -> pick(d, "totalPaidMinor", "currency");
            default -> Map.of();
        };
    }

    private static Map<String, Object> pick(Map<String, Object> data, String... keys) {
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        for (String key : keys) {
            if (data.containsKey(key)) {
                out.put(key, data.get(key));
            }
        }
        return out;
    }
}
