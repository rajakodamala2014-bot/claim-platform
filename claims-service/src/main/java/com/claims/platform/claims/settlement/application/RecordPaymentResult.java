package com.claims.platform.claims.settlement.application;

import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.UnitOfWork;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.common.payments.PaymentResult;

import java.time.Clock;
import java.util.Optional;

/**
 * Applies a result from the Payments system (Kafka {@code payments.payment-results}, or the signed webhook
 * fallback). Idempotent: duplicates and results for superseded attempts are ignored.
 * PAID -> SETTLED + reserve released; FAILED -> handler task (PaymentFailed), status unchanged.
 */
public class RecordPaymentResult {
    public enum Outcome { APPLIED, IGNORED_DUPLICATE, UNKNOWN_CLAIM }

    private final ClaimRepository claims;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RecordPaymentResult(ClaimRepository claims, UnitOfWork unitOfWork, Clock clock) {
        this.claims = claims;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public Outcome record(PaymentResult result) {
        return unitOfWork.inTransaction(() -> {
            Optional<Claim> found = claims.findById(new ClaimId(result.claimId()));
            if (found.isEmpty()) {
                return Outcome.UNKNOWN_CLAIM;
            }
            Claim claim = found.get();
            boolean applied = switch (result.status()) {
                case PAID -> claim.confirmPayment(result.settlementId(), result.instructionId(), result.paymentRef(),
                        result.processedAt() == null ? clock.instant() : result.processedAt(), clock.instant());
                case FAILED -> claim.failPayment(result.settlementId(), result.instructionId(),
                        result.failureReason() == null ? "UNKNOWN" : result.failureReason(), clock.instant());
            };
            if (!applied) {
                return Outcome.IGNORED_DUPLICATE;
            }
            claims.save(claim);
            return Outcome.APPLIED;
        });
    }
}
