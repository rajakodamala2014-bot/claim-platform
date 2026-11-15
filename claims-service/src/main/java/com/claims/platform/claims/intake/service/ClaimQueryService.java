package com.claims.platform.claims.intake.service;

import com.claims.platform.claims.core.dto.response.AuditEntryResponse;
import com.claims.platform.claims.core.dto.response.TimelineEntryResponse;
import com.claims.platform.claims.core.common.Page;
import com.claims.platform.claims.core.common.PageRequest;
import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.mapper.StaffClaimMapper;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.intake.dto.response.ClaimantClaimDetailResponse;
import com.claims.platform.claims.intake.dto.response.ClaimantClaimSummaryResponse;
import com.claims.platform.claims.intake.dto.response.StaffClaimWorkspaceResponse;
import java.util.List;

/** Reading claims: the customer's own claims (C4-C6) and the staff workspace and audit trail (S2, S3). */
public interface ClaimQueryService {
    /** C5 detail and C6 timeline of one of my claimAccess (404 for anyone else's). */
    ClaimantClaimDetailResponse getClaimantClaim(ClaimId claimId, String claimantId);

    /** C5 detail and C6 timeline of one of my claimAccess (404 for anyone else's). */
    List<TimelineEntryResponse> getClaimantClaimTimeline(ClaimId claimId, String claimantId);

    /** C4: my claims, newest first. */
    Page<ClaimantClaimSummaryResponse> listClaimantClaims(String claimantId, PageRequest page);

    /** S2 claim workspace (claim + intake records) and S3 full internal audit trail. Market-scoped. */
    StaffClaimWorkspaceResponse getStaffClaimWorkspace(ClaimId claimId, StaffContext staff);

    /** S2 claim workspace (claim + intake records) and S3 full internal audit trail. Market-scoped. */
    List<AuditEntryResponse> getStaffClaimAudit(ClaimId claimId, StaffContext staff);
}
