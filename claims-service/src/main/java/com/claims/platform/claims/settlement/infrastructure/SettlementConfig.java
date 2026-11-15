package com.claims.platform.claims.settlement.infrastructure;

import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.UnitOfWork;
import com.claims.platform.claims.settlement.application.InstructSettlement;
import com.claims.platform.claims.settlement.application.RecordPaymentResult;
import com.claims.platform.claims.settlement.application.RetrySettlement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the settlement module's use cases. */
@Configuration
public class SettlementConfig {

    @Bean
    InstructSettlement instructSettlement(StaffClaims staffClaims, Clock clock) {
        return new InstructSettlement(staffClaims, clock);
    }

    @Bean
    RetrySettlement retrySettlement(StaffClaims staffClaims, Clock clock) {
        return new RetrySettlement(staffClaims, clock);
    }

    @Bean
    RecordPaymentResult recordPaymentResult(ClaimRepository claims, UnitOfWork unitOfWork, Clock clock) {
        return new RecordPaymentResult(claims, unitOfWork, clock);
    }
}
