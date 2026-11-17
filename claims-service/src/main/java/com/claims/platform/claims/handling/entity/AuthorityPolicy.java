package com.claims.platform.claims.handling.entity;

import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.ApprovalRequirement;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.core.entity.Role;
import com.claims.platform.common.money.Money;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Every decision waits for a second claim manager. The configured limit is kept so a mismatched
 * currency is still rejected. The proposer cannot approve their own decision; that rule lives on the claim.
 */
public final class AuthorityPolicy {
    private record Key(Role role, MarketCode market, LineOfBusiness lineOfBusiness) {
    }

    private final Map<Key, Money> limits = new HashMap<>();

    public AuthorityPolicy(Collection<AuthorityLimit> configured) {
        for (AuthorityLimit limit : configured) {
            Key key = new Key(limit.role(), limit.market(), limit.lineOfBusiness());
            if (limits.putIfAbsent(key, limit.maxAmount()) != null) {
                throw new IllegalArgumentException("Duplicate authority limit for " + key);
            }
        }
    }

    public Optional<Money> limitFor(Role role, MarketCode market, LineOfBusiness lineOfBusiness) {
        return Optional.ofNullable(limits.get(new Key(role, market, lineOfBusiness)));
    }

    /**
     * A proposal is never final on its own. Another claim manager approves it.
     * When a limit is configured, the amount must be in that limit's currency.
     */
    public ApprovalRequirement requirementFor(Actor proposer, MarketCode market, LineOfBusiness lineOfBusiness, Money amount) {
        if (!proposer.role().isStaff()) {
            throw new DomainException(ErrorCode.FORBIDDEN_ROLE, "Only staff can propose decisions");
        }
        limitFor(proposer.role(), market, lineOfBusiness).ifPresent(limit -> {
            if (!limit.currency().equals(amount.currency())) {
                throw new DomainException(ErrorCode.CURRENCY_MISMATCH,
                        "Authority limit is in " + limit.currency() + " but the amount is in " + amount.currency());
            }
        });
        return ApprovalRequirement.SUPERVISOR_APPROVAL;
    }

    /**
     * Another claims adjuster may approve a decision the proposer could not finalise.
     * The amount ceiling applies when the decision is proposed, not again here.
     */
    public void requireCanApprove(Actor approver, MarketCode market, LineOfBusiness lineOfBusiness, Money amount) {
        if (!approver.role().isSupervisor()) {
            throw new DomainException(ErrorCode.FORBIDDEN_ROLE, "Only a claims adjuster can approve decisions");
        }
    }

}
