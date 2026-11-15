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
 * Authority limits per role / market / line of business.
 * <ul>
 *     <li>Proposal within the proposer's limit (inclusive) -> final straight away.</li>
 *     <li>Above it, or no limit configured -> a supervisor must approve.</li>
 *     <li>An approver must be a TEAM_LEAD/MANAGER whose own limit covers the amount; otherwise
 *     AMOUNT_EXCEEDS_AUTHORITY (it escalates to someone with a higher limit, e.g. a manager).</li>
 *     <li>"Proposer cannot approve their own decision" is enforced by the Claim aggregate.</li>
 * </ul>
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

    /** Does a decision of {@code amount} proposed by {@code proposer} need a supervisor's approval? */
    public ApprovalRequirement requirementFor(Actor proposer, MarketCode market, LineOfBusiness lineOfBusiness, Money amount) {
        if (!proposer.role().isStaff()) {
            throw new DomainException(ErrorCode.FORBIDDEN_ROLE, "Only staff can propose decisions");
        }
        return limitFor(proposer.role(), market, lineOfBusiness)
                .filter(limit -> covers(limit, amount))
                .map(limit -> ApprovalRequirement.NONE)
                .orElse(ApprovalRequirement.SUPERVISOR_APPROVAL);
    }

    /** Throws unless {@code approver} may approve a decision of {@code amount}. */
    public void requireCanApprove(Actor approver, MarketCode market, LineOfBusiness lineOfBusiness, Money amount) {
        if (!approver.role().isSupervisor()) {
            throw new DomainException(ErrorCode.FORBIDDEN_ROLE, "Only a team lead or manager can approve decisions");
        }
        Money limit = limitFor(approver.role(), market, lineOfBusiness)
                .orElseThrow(() -> new DomainException(ErrorCode.AMOUNT_EXCEEDS_AUTHORITY,
                        "No approval authority for " + market + " " + lineOfBusiness));
        if (!covers(limit, amount)) {
            throw new DomainException(ErrorCode.AMOUNT_EXCEEDS_AUTHORITY,
                    "Amount " + amount + " exceeds your approval limit of " + limit);
        }
    }

    private static boolean covers(Money limit, Money amount) {
        if (!limit.currency().equals(amount.currency())) {
            throw new DomainException(ErrorCode.CURRENCY_MISMATCH,
                    "Authority limit is in " + limit.currency() + " but the amount is in " + amount.currency());
        }
        return !amount.isGreaterThan(limit);
    }
}
