package com.claims.platform.claims.intake.service;

import com.claims.platform.claims.core.dto.response.AuditEntryResponse;
import com.claims.platform.claims.core.dto.response.TimelineEntryResponse;
import com.claims.platform.claims.core.common.Page;
import com.claims.platform.claims.core.common.PageRequest;
import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.intake.dto.response.ClaimantClaimDetailResponse;
import com.claims.platform.claims.intake.dto.response.ClaimantClaimSummaryResponse;
import com.claims.platform.claims.intake.dto.response.StaffClaimWorkspaceResponse;
import java.util.List;

/** Reading claims: the customer's own claims (C4-C6) and the staff workspace and audit trail (S2, S3). */
public interface ClaimQueryService {
    /** C5. One claim the customer owns. Someone else's claim is reported as not found. */
    ClaimantClaimDetailResponse getClaimantClaim(ClaimId claimId, String claimantId);

    /** C6. Customer-visible events for a claim the customer owns. */
    List<TimelineEntryResponse> getClaimantClaimTimeline(ClaimId claimId, String claimantId);

    /** C4: my claims, newest first. */
    Page<ClaimantClaimSummaryResponse> listClaimantClaims(String claimantId, PageRequest page);

    /** S2. Claim workspace for a staff member whose markets include the claim. */
    StaffClaimWorkspaceResponse getStaffClaimWorkspace(ClaimId claimId, StaffContext staff);

    /** S3. Full audit trail, including internal events, for a staff member who can see the claim. */
    List<AuditEntryResponse> getStaffClaimAudit(ClaimId claimId, StaffContext staff);
}
