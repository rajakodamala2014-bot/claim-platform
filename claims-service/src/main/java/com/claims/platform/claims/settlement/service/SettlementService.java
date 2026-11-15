package com.claims.platform.claims.settlement.service;

import com.claims.platform.claims.core.common.StaffContext;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.Payee;
import com.claims.platform.claims.core.entity.Settlement;
import com.claims.platform.claims.core.service.StaffClaimCommandService.Outcome;
import com.claims.platform.common.money.Money;
import java.util.UUID;

/** Instructing and retrying payment of an approved claim (S11, S12). */
public interface SettlementService {
    /**
     * S11: instruct payment (amount <= approved). APPROVED -> SETTLEMENT_IN_PROGRESS. The payment instruction is
     * written to the outbox for {@code payments.settlement-requests} in the same transaction.
     */
    Outcome<Settlement> instructSettlement(ClaimId claimId, StaffContext staff, long expectedVersion, Payee payee, Money amount);

    /** S12: retry a FAILED payment, optionally with corrected bank details, as a new instruction. */
    Outcome<Settlement> retrySettlement(ClaimId claimId, UUID settlementId, StaffContext staff, long expectedVersion,
            Payee correctedPayee);
}
