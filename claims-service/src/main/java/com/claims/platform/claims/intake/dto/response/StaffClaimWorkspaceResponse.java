package com.claims.platform.claims.intake.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.dto.response.StaffClaimResponse;
import com.claims.platform.claims.core.mapper.StaffClaimMapper;
import com.claims.platform.claims.intake.entity.IncidentDetails;
import com.claims.platform.claims.intake.entity.Party;
import com.claims.platform.claims.intake.entity.PolicySnapshot;
import java.util.List;

@Schema(description = "Everything a handler needs: the claim, the incident, the parties and the policy as it was at submission")
public record StaffClaimWorkspaceResponse(StaffClaimResponse claim, IncidentDetails incident, List<Party> parties, PolicySnapshot policy) {
}
