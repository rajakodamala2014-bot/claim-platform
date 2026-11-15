package com.claims.platform.claims.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

@Schema(description = "Optionally corrected payee details for the next payment attempt")
public record RetrySettlementRequest(@Valid PayeeRequest payee) {
}
