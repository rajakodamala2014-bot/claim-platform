package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Payment of an approved claim as the customer sees it")
public record ClaimantPaymentResponse(String status, MoneyResponse amount, Instant paidAt, String paymentRef) {
}
