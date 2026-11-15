package com.claims.platform.claims.settlement.service.impl;

import com.claims.platform.claims.core.repository.ClaimRepository;
import com.claims.platform.claims.core.service.UnitOfWork;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.settlement.service.PaymentResultService;
import com.claims.platform.common.payments.PaymentResult;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PaymentResultServiceImpl implements PaymentResultService {
    private final ClaimRepository claims;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public PaymentResultServiceImpl(ClaimRepository claims, UnitOfWork unitOfWork, Clock clock) {
        this.claims = claims;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public PaymentResultOutcome recordPaymentResult(PaymentResult result) {
        return unitOfWork.inTransaction(() -> {
            Optional<Claim> found = claims.findById(new ClaimId(result.claimId()));
            if (found.isEmpty()) {
                return PaymentResultOutcome.UNKNOWN_CLAIM;
            }
            Claim claim = found.get();
            boolean applied = switch (result.status()) {
                case PAID -> claim.confirmPayment(result.settlementId(), result.instructionId(), result.paymentRef(),
                        result.processedAt() == null ? clock.instant() : result.processedAt(), clock.instant());
                case FAILED -> claim.failPayment(result.settlementId(), result.instructionId(),
                        result.failureReason() == null ? "UNKNOWN" : result.failureReason(), clock.instant());
            };
            if (!applied) {
                return PaymentResultOutcome.IGNORED_DUPLICATE;
            }
            claims.save(claim);
            return PaymentResultOutcome.APPLIED;
        });
    }
}
