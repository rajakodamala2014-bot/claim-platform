package com.claims.platform.claims.settlement.service;

import com.claims.platform.common.payments.PaymentResult;

/** Applies payment results from the Payments system (Kafka topic or signed webhook). */
public interface PaymentResultService {
    enum PaymentResultOutcome { APPLIED, IGNORED_DUPLICATE, UNKNOWN_CLAIM }

    /**
     * Applies a result from the Payments system (Kafka {@code payments.payment-results}, or the signed webhook
     * fallback). Idempotent: duplicates and results for superseded attempts are ignored.
     * PAID -> SETTLED + reserve released; FAILED -> handler task (PaymentFailed), status unchanged.
     */
    PaymentResultOutcome recordPaymentResult(PaymentResult result);
}
