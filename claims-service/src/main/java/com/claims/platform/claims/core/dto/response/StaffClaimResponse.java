package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A claim as staff see it: status, cover, assignment, SLA, reserve, assessment, decision, settlement and documents")
public record StaffClaimResponse(
        UUID claimId, String claimNumber, String market, String lineOfBusiness, String policyNumber,
        String claimantId, String channel, String status, Instant statusChangedAt, String coverStatus,
        String coverDeclineReason, String handlerId, String teamId, Instant submittedAt, Instant incidentAt,
        MoneyResponse claimedAmount, SlaResponse sla, ReserveResponse reserve, List<StaffInformationRequestResponse> informationRequests,
        AssessmentResponse assessment, StaffDecisionResponse decision, MoneyResponse approvedAmount, SettlementResponse settlement,
        List<StaffDocumentResponse> documents, long version) {
}
