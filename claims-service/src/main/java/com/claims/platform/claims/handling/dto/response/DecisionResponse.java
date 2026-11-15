package com.claims.platform.claims.handling.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.dto.response.MoneyResponse;
import java.util.UUID;

@Schema(description = "The proposed decision and whether it still needs approval")
public record DecisionResponse(UUID decisionId, String outcome, MoneyResponse amount, boolean approvalRequired, String decisionStatus) {
}
