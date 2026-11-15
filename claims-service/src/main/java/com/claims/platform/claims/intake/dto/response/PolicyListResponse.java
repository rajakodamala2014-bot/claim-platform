package com.claims.platform.claims.intake.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "The active policies of the customer")
public record PolicyListResponse(List<PolicyResponse> items) {
}
