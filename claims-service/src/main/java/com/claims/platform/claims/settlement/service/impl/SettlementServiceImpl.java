package com.claims.platform.claims.settlement.service.impl;

import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.Payee;
import com.claims.platform.claims.core.entity.Settlement;
import com.claims.platform.claims.core.service.StaffClaimCommandService.Outcome;
import com.claims.platform.claims.core.service.StaffClaimCommandService;
import com.claims.platform.claims.settlement.service.SettlementService;
import com.claims.platform.common.money.Money;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SettlementServiceImpl implements SettlementService {
    private final StaffClaimCommandService staffCommands;
    private final Clock clock;

    public SettlementServiceImpl(StaffClaimCommandService staffCommands, Clock clock) {
        this.staffCommands = staffCommands;
        this.clock = clock;
    }

    @Override
    public Outcome<Settlement> instructSettlement(ClaimId claimId, StaffContext staff, long expectedVersion, Payee payee, Money amount) {
        UUID settlementId = UUID.randomUUID();
        UUID instructionId = UUID.randomUUID();
        return staffCommands.applyChange(claimId, staff, expectedVersion, claim ->
                claim.instructSettlement(settlementId, instructionId, payee, amount, staff.actor(), clock.instant()));
    }

    @Override
    public Outcome<Settlement> retrySettlement(ClaimId claimId, UUID settlementId, StaffContext staff, long expectedVersion,
                                     Payee correctedPayee) {
        UUID instructionId = UUID.randomUUID();
        return staffCommands.applyChange(claimId, staff, expectedVersion, claim ->
                claim.retrySettlement(settlementId, instructionId, correctedPayee, staff.actor(), clock.instant()));
    }
}
