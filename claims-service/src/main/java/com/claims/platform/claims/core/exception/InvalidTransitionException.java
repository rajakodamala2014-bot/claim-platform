package com.claims.platform.claims.core.exception;

import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimStatus;

public class InvalidTransitionException extends DomainException {
    private final ClaimStatus from;
    private final ClaimStatus to;

    public InvalidTransitionException(ClaimStatus from, ClaimStatus to) {
        super(ErrorCode.CLAIM_INVALID_TRANSITION, "Claim cannot move from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public ClaimStatus from() {
        return from;
    }

    public ClaimStatus to() {
        return to;
    }
}
