package com.claims.platform.claims.intake.service.impl;

import com.claims.platform.claims.core.dto.response.AuditEntryResponse;
import com.claims.platform.claims.core.dto.response.TimelineEntryResponse;
import com.claims.platform.claims.core.repository.ClaimQueryRepository.ClaimSummary;
import com.claims.platform.claims.core.repository.ClaimQueryRepository;
import com.claims.platform.claims.core.mapper.ClaimantClaimMapper;
import com.claims.platform.claims.core.repository.DocumentRepository;
import com.claims.platform.claims.core.common.Page;
import com.claims.platform.claims.core.common.PageRequest;
import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.mapper.StaffClaimMapper;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.ClaimStatus;
import com.claims.platform.claims.core.service.ClaimantClaimAccessService;
import com.claims.platform.claims.core.service.StaffClaimCommandService;
import com.claims.platform.claims.intake.repository.IntakeRepository;
import com.claims.platform.claims.intake.dto.response.ClaimantClaimDetailResponse;
import com.claims.platform.claims.intake.dto.response.ClaimantClaimSummaryResponse;
import com.claims.platform.claims.intake.dto.response.StaffClaimWorkspaceResponse;
import com.claims.platform.claims.intake.service.ClaimQueryService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ClaimQueryServiceImpl implements ClaimQueryService {
    private final ClaimantClaimAccessService claimAccess;
    private final DocumentRepository documents;
    private final ClaimQueryRepository queries;
    private final StaffClaimCommandService staffCommands;
    private final IntakeRepository intake;

    public ClaimQueryServiceImpl(ClaimantClaimAccessService claimAccess, DocumentRepository documents,
                                 ClaimQueryRepository queries, StaffClaimCommandService staffCommands,
                                 IntakeRepository intake) {
        this.claimAccess = claimAccess;
        this.documents = documents;
        this.queries = queries;
        this.staffCommands = staffCommands;
        this.intake = intake;
    }

    @Override
    public ClaimantClaimDetailResponse getClaimantClaim(ClaimId claimId, String claimantId) {
        Claim claim = claimAccess.loadOwnedClaim(claimId, claimantId);
        return new ClaimantClaimDetailResponse(ClaimantClaimMapper.toClaimResponse(claim, documents.findByClaimId(claimId)), claim.version());
    }

    @Override
    public List<TimelineEntryResponse> getClaimantClaimTimeline(ClaimId claimId, String claimantId) {
        claimAccess.loadOwnedClaim(claimId, claimantId);
        return ClaimantClaimMapper.toTimeline(queries.events(claimId, true));
    }

    @Override
    public Page<ClaimantClaimSummaryResponse> listClaimantClaims(String claimantId, PageRequest page) {
        return queries.findClaimsOfClaimant(claimantId, page).map(ClaimQueryServiceImpl::toClaimSummaryResponse);
    }

    private static ClaimantClaimSummaryResponse toClaimSummaryResponse(ClaimSummary s) {
        return new ClaimantClaimSummaryResponse(s.claimId(), s.claimNumber(), s.status(), ClaimantClaimMapper.toStatusLabel(ClaimStatus.valueOf(s.status())),
                s.lineOfBusiness(), s.policyNumber(), s.submittedAt(), s.statusChangedAt(), s.actionNeeded());
    }

    @Override
    public StaffClaimWorkspaceResponse getStaffClaimWorkspace(ClaimId claimId, StaffContext staff) {
        Claim claim = staffCommands.loadVisibleClaim(claimId, staff);
        return new StaffClaimWorkspaceResponse(StaffClaimMapper.toClaimResponse(claim, documents.findByClaimId(claimId)),
                intake.findIncident(claimId).orElse(null), intake.findParties(claimId),
                intake.findPolicySnapshot(claimId).orElse(null));
    }

    @Override
    public List<AuditEntryResponse> getStaffClaimAudit(ClaimId claimId, StaffContext staff) {
        staffCommands.loadVisibleClaim(claimId, staff);
        return StaffClaimMapper.toAuditEntries(queries.events(claimId, false));
    }
}
