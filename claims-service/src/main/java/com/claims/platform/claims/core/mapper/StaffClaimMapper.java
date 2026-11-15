package com.claims.platform.claims.core.mapper;

import com.claims.platform.claims.core.dto.response.MoneyResponse;
import com.claims.platform.claims.core.dto.response.AuditEntryResponse;
import com.claims.platform.claims.core.dto.response.StaffDocumentResponse;
import com.claims.platform.claims.core.dto.response.SettlementResponse;
import com.claims.platform.claims.core.dto.response.StaffDecisionResponse;
import com.claims.platform.claims.core.dto.response.AssessmentResponse;
import com.claims.platform.claims.core.dto.response.StaffInformationRequestResponse;
import com.claims.platform.claims.core.dto.response.ReserveMovementResponse;
import com.claims.platform.claims.core.dto.response.ReserveResponse;
import com.claims.platform.claims.core.dto.response.SlaResponse;
import com.claims.platform.claims.core.dto.response.StaffClaimResponse;
import com.claims.platform.claims.core.entity.Assessment;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimEvent;
import com.claims.platform.claims.core.entity.Decision;
import com.claims.platform.claims.core.entity.Document;
import com.claims.platform.claims.core.entity.InformationRequest;
import com.claims.platform.claims.core.entity.ReserveMovement;
import com.claims.platform.claims.core.entity.Settlement;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff-facing projections of the claim aggregate: everything a handler needs (reserves, notes, decision
 * trail, cover flags). Bank account numbers stay masked even here; only the Payments system gets them.
 */
public final class StaffClaimMapper {
    private StaffClaimMapper() {
    }

    public static StaffClaimResponse toClaimResponse(Claim c, List<Document> documents) {
        return new StaffClaimResponse(c.id().value(), c.number().value(), c.market().value(), c.lineOfBusiness().name(),
                c.policyNumber(), c.claimantId(), c.channel().name(), c.status().name(), c.statusChangedAt(),
                c.coverStatus().name(), c.coverDeclineReason().orElse(null), c.handlerId().orElse(null),
                c.teamId().orElse(null), c.submittedAt(), c.incidentDate(), c.claimedAmount().map(MoneyResponse::of).orElse(null),
                new SlaResponse(c.slaDueAt(), c.isSlaPaused(), c.slaPausedAt().orElse(null)),
                new ReserveResponse(MoneyResponse.of(c.outstandingReserve()), c.reserveMovements().stream().map(StaffClaimMapper::toReserveMovementResponse).toList()),
                c.informationRequests().stream().map(StaffClaimMapper::toInformationRequestResponse).toList(),
                c.assessment().map(StaffClaimMapper::toAssessmentResponse).orElse(null),
                c.decision().map(StaffClaimMapper::toDecisionResponse).orElse(null),
                c.approvedAmount().map(MoneyResponse::of).orElse(null),
                c.settlement().map(StaffClaimMapper::toSettlementResponse).orElse(null),
                documents.stream().map(d -> new StaffDocumentResponse(d.id(), d.fileName(), d.contentType(), d.sizeBytes(),
                        d.status().name(), d.uploadedAt())).toList(),
                c.version());
    }

    public static List<AuditEntryResponse> toAuditEntries(List<ClaimEvent> events) {
        return events.stream().map(e -> new AuditEntryResponse(e.eventId(), e.occurredAt(), e.type(), e.visibility().name(),
                e.actorId(), e.actorRole().name(), e.data())).toList();
    }

    private static ReserveMovementResponse toReserveMovementResponse(ReserveMovement m) {
        return new ReserveMovementResponse(m.id(), m.type().name(), MoneyResponse.of(m.amount()), MoneyResponse.of(m.balanceAfter()), m.reason(),
                m.actorId(), m.at());
    }

    private static StaffInformationRequestResponse toInformationRequestResponse(InformationRequest r) {
        return new StaffInformationRequestResponse(r.id(), r.items(), r.dueAt(), r.requestedBy(), r.requestedAt(), r.status().name(),
                r.answer(), r.documentIds(), r.closedAt());
    }

    private static AssessmentResponse toAssessmentResponse(Assessment a) {
        return new AssessmentResponse(a.id(), a.liability().name(), MoneyResponse.of(a.assessedAmount()), a.notes(),
                a.assessedBy(), a.assessedAt());
    }

    private static StaffDecisionResponse toDecisionResponse(Decision d) {
        return new StaffDecisionResponse(d.id(), d.outcome().name(), MoneyResponse.of(d.amount()), d.reasonCode(), d.proposedBy(),
                d.proposedAt(), d.approval() != com.claims.platform.claims.core.entity.ApprovalRequirement.NONE,
                d.status().name(), d.decidedBy(), d.decidedAt());
    }

    private static SettlementResponse toSettlementResponse(Settlement s) {
        String account = s.payee().accountNumber();
        String masked = "****" + account.substring(Math.max(0, account.length() - 4));
        return new SettlementResponse(s.id(), MoneyResponse.of(s.amount()), s.payee().name(), s.payee().bankCode(), masked,
                s.instructionId(), s.attempts(), s.status().name(), s.paymentRef(), s.failureReason(), s.instructedAt(),
                s.completedAt());
    }
}
