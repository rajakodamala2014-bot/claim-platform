package com.claims.platform.claims.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What happened to the payment result: APPLIED, IGNORED_DUPLICATE or UNKNOWN_CLAIM")
public record PaymentCallbackResponse(String outcome) {
}
