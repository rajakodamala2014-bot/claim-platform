package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A proposed or final decision, with who proposed and who approved it")
public record StaffDecisionResponse(UUID decisionId, String outcome, MoneyResponse amount, String reasonCode, String proposedBy,
                           Instant proposedAt, boolean approvalRequired, String status, String decidedBy,
                           Instant decidedAt) {
}
