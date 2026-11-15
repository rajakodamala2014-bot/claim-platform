package com.claims.platform.claims.core.application;

import com.claims.platform.claims.core.application.ClaimantViews.MoneyView;
import com.claims.platform.claims.core.domain.Assessment;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimEvent;
import com.claims.platform.claims.core.domain.Decision;
import com.claims.platform.claims.core.domain.Document;
import com.claims.platform.claims.core.domain.InformationRequest;
import com.claims.platform.claims.core.domain.ReserveMovement;
import com.claims.platform.claims.core.domain.Settlement;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff-facing projections of the claim aggregate: everything a handler needs (reserves, notes, decision
 * trail, cover flags). Bank account numbers stay masked even here; only the Payments system gets them.
 */
public final class StaffViews {
    private StaffViews() {
    }

    public record ClaimCore(
            UUID claimId, String claimNumber, String market, String lineOfBusiness, String policyNumber,
            String claimantId, String channel, String status, Instant statusChangedAt, String coverStatus,
            String coverDeclineReason, String handlerId, String teamId, Instant submittedAt, Instant incidentAt,
            MoneyView claimedAmount, Sla sla, Reserve reserve, List<InfoRequest> informationRequests,
            AssessmentView assessment, DecisionView decision, MoneyView approvedAmount, SettlementView settlement,
            List<DocumentView> documents, long version) {
    }

    public record Sla(Instant dueAt, boolean paused, Instant pausedAt) {
    }

    public record Reserve(MoneyView outstanding, List<Movement> movements) {
    }

    public record Movement(UUID id, String type, MoneyView amount, MoneyView balanceAfter, String reason, String actorId,
                           Instant at) {
    }

    public record InfoRequest(UUID requestId, List<String> items, Instant dueAt, String requestedBy, Instant requestedAt,
                              String status, String answer, List<UUID> documentIds, Instant closedAt) {
    }

    public record AssessmentView(UUID assessmentId, String liability, MoneyView assessedAmount, String notes,
                                 String assessedBy, Instant assessedAt) {
    }

    public record DecisionView(UUID decisionId, String outcome, MoneyView amount, String reasonCode, String proposedBy,
                               Instant proposedAt, boolean approvalRequired, String status, String decidedBy,
                               Instant decidedAt) {
    }

    public record SettlementView(UUID settlementId, MoneyView amount, String payeeName, String bankCode,
                                 String accountNumberMasked, UUID instructionId, int attempts, String status,
                                 String paymentRef, String failureReason, Instant instructedAt, Instant completedAt) {
    }

    public record DocumentView(UUID documentId, String fileName, String contentType, long sizeBytes, String status,
                               Instant uploadedAt) {
    }

    public record AuditEntry(UUID eventId, Instant at, String type, String visibility, String actorId, String actorRole,
                             Map<String, Object> data) {
    }

    public static ClaimCore claim(Claim c, List<Document> documents) {
        return new ClaimCore(c.id().value(), c.number().value(), c.market().value(), c.lineOfBusiness().name(),
                c.policyNumber(), c.claimantId(), c.channel().name(), c.status().name(), c.statusChangedAt(),
                c.coverStatus().name(), c.coverDeclineReason().orElse(null), c.handlerId().orElse(null),
                c.teamId().orElse(null), c.submittedAt(), c.incidentDate(), c.claimedAmount().map(MoneyView::of).orElse(null),
                new Sla(c.slaDueAt(), c.isSlaPaused(), c.slaPausedAt().orElse(null)),
                new Reserve(MoneyView.of(c.outstandingReserve()), c.reserveMovements().stream().map(StaffViews::movement).toList()),
                c.informationRequests().stream().map(StaffViews::infoRequest).toList(),
                c.assessment().map(StaffViews::assessment).orElse(null),
                c.decision().map(StaffViews::decision).orElse(null),
                c.approvedAmount().map(MoneyView::of).orElse(null),
                c.settlement().map(StaffViews::settlement).orElse(null),
                documents.stream().map(d -> new DocumentView(d.id(), d.fileName(), d.contentType(), d.sizeBytes(),
                        d.status().name(), d.uploadedAt())).toList(),
                c.version());
    }

    public static List<AuditEntry> audit(List<ClaimEvent> events) {
        return events.stream().map(e -> new AuditEntry(e.eventId(), e.occurredAt(), e.type(), e.visibility().name(),
                e.actorId(), e.actorRole().name(), e.data())).toList();
    }

    private static Movement movement(ReserveMovement m) {
        return new Movement(m.id(), m.type().name(), MoneyView.of(m.amount()), MoneyView.of(m.balanceAfter()), m.reason(),
                m.actorId(), m.at());
    }

    private static InfoRequest infoRequest(InformationRequest r) {
        return new InfoRequest(r.id(), r.items(), r.dueAt(), r.requestedBy(), r.requestedAt(), r.status().name(),
                r.answer(), r.documentIds(), r.closedAt());
    }

    private static AssessmentView assessment(Assessment a) {
        return new AssessmentView(a.id(), a.liability().name(), MoneyView.of(a.assessedAmount()), a.notes(),
                a.assessedBy(), a.assessedAt());
    }

    private static DecisionView decision(Decision d) {
        return new DecisionView(d.id(), d.outcome().name(), MoneyView.of(d.amount()), d.reasonCode(), d.proposedBy(),
                d.proposedAt(), d.approval() != com.claims.platform.claims.core.domain.ApprovalRequirement.NONE,
                d.status().name(), d.decidedBy(), d.decidedAt());
    }

    private static SettlementView settlement(Settlement s) {
        String account = s.payee().accountNumber();
        String masked = "****" + account.substring(Math.max(0, account.length() - 4));
        return new SettlementView(s.id(), MoneyView.of(s.amount()), s.payee().name(), s.payee().bankCode(), masked,
                s.instructionId(), s.attempts(), s.status().name(), s.paymentRef(), s.failureReason(), s.instructedAt(),
                s.completedAt());
    }
}
