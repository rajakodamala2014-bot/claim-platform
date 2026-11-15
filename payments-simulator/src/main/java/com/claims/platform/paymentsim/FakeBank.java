package com.claims.platform.paymentsim;

import com.claims.platform.common.payments.PaymentResult;
import com.claims.platform.common.payments.SettlementRequest;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic bank rules so demos and tests can force either outcome:
 * <ul>
 *     <li>account number ending in {@code 0000} -> FAILED / ACCOUNT_CLOSED</li>
 *     <li>non-positive amount -> FAILED / INVALID_AMOUNT</li>
 *     <li>otherwise -> PAID with a payment reference</li>
 * </ul>
 * Redelivered instructions (same instructionId) get the same answer, as a real payments system would.
 */
public class FakeBank {
    private final Clock clock;
    private final Map<java.util.UUID, PaymentResult> processed = new ConcurrentHashMap<>();

    public FakeBank(Clock clock) {
        this.clock = clock;
    }

    public PaymentResult process(SettlementRequest request) {
        return processed.computeIfAbsent(request.instructionId(), id -> decide(request));
    }

    private PaymentResult decide(SettlementRequest request) {
        String failure = null;
        if (request.accountNumber() == null || request.accountNumber().endsWith("0000")) {
            failure = "ACCOUNT_CLOSED";
        } else if (request.amountMinor() <= 0) {
            failure = "INVALID_AMOUNT";
        }
        if (failure != null) {
            return result(request, PaymentResult.Status.FAILED, null, failure);
        }
        String reference = "PAY-" + request.instructionId().toString().substring(0, 8).toUpperCase();
        return result(request, PaymentResult.Status.PAID, reference, null);
    }

    private PaymentResult result(SettlementRequest request, PaymentResult.Status status, String reference, String failure) {
        return new PaymentResult(request.instructionId(), request.settlementId(), request.claimId(), status,
                reference, failure, request.amountMinor(), request.currency(), clock.instant());
    }
}
