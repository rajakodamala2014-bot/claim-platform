package com.claims.platform.claims.domain;

import com.claims.platform.common.error.ErrorCodes;
import com.claims.platform.web.ApiException;

import java.util.Map;
import java.util.Set;

public final class ClaimTransitions {
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            ClaimStatuses.SUBMITTED, Set.of(ClaimStatuses.TRIAGED, ClaimStatuses.IN_REVIEW),
            ClaimStatuses.TRIAGED, Set.of(ClaimStatuses.IN_REVIEW),
            ClaimStatuses.IN_REVIEW, Set.of(ClaimStatuses.AWAITING_INFO, ClaimStatuses.ASSESSED, ClaimStatuses.APPROVED, ClaimStatuses.REJECTED),
            ClaimStatuses.AWAITING_INFO, Set.of(ClaimStatuses.IN_REVIEW),
            ClaimStatuses.ASSESSED, Set.of(ClaimStatuses.IN_REVIEW, ClaimStatuses.AWAITING_INFO, ClaimStatuses.APPROVED, ClaimStatuses.REJECTED),
            ClaimStatuses.APPROVED, Set.of(ClaimStatuses.SETTLED),
            ClaimStatuses.REJECTED, Set.of(ClaimStatuses.CLOSED),
            ClaimStatuses.SETTLED, Set.of(ClaimStatuses.CLOSED),
            ClaimStatuses.CLOSED, Set.of(ClaimStatuses.IN_REVIEW)
    );

    private ClaimTransitions() {
    }

    public static void require(String from, String to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw ApiException.unprocessable(ErrorCodes.INVALID_STATE_TRANSITION,
                    "Claim cannot move from " + from + " to " + to);
        }
    }
}
