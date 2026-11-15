package com.claims.platform.claims.intake.application;

import com.claims.platform.claims.core.application.ClaimQueries;
import com.claims.platform.claims.core.application.DocumentRepository;
import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.application.StaffViews;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.intake.domain.IncidentDetails;
import com.claims.platform.claims.intake.domain.Party;
import com.claims.platform.claims.intake.domain.PolicySnapshot;

import java.util.List;

/** S2 claim workspace (claim + intake records) and S3 full internal audit trail. Market-scoped. */
public class GetStaffClaim {
    private final StaffClaims staffClaims;
    private final DocumentRepository documents;
    private final IntakeRecords intake;
    private final ClaimQueries queries;

    public GetStaffClaim(StaffClaims staffClaims, DocumentRepository documents, IntakeRecords intake, ClaimQueries queries) {
        this.staffClaims = staffClaims;
        this.documents = documents;
        this.intake = intake;
        this.queries = queries;
    }

    public record Workspace(StaffViews.ClaimCore claim, IncidentDetails incident, List<Party> parties, PolicySnapshot policy) {
    }

    public Workspace workspace(ClaimId claimId, StaffContext staff) {
        Claim claim = staffClaims.loadVisible(claimId, staff);
        return new Workspace(StaffViews.claim(claim, documents.findByClaim(claimId)),
                intake.findIncident(claimId).orElse(null), intake.findParties(claimId),
                intake.findPolicySnapshot(claimId).orElse(null));
    }

    public List<StaffViews.AuditEntry> audit(ClaimId claimId, StaffContext staff) {
        staffClaims.loadVisible(claimId, staff);
        return StaffViews.audit(queries.events(claimId, false));
    }
}
