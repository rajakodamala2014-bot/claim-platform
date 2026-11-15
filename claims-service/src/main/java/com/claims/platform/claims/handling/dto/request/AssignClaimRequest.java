package com.claims.platform.claims.handling.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Who should handle the claim; leave empty to pick it up yourself")
public record AssignClaimRequest(@Size(max = 100) String assigneeId, @Size(max = 100) String teamId) {
}
