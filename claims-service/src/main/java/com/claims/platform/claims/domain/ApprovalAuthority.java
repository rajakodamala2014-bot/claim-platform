package com.claims.platform.claims.domain;

import com.claims.platform.common.error.ErrorCodes;
import com.claims.platform.web.ApiException;

import java.math.BigDecimal;
import java.util.Map;

public final class ApprovalAuthority {
    private ApprovalAuthority() {
    }

    public static void check(String role, BigDecimal amount, String currency, Map<String, Object> limits) {
        Object raw = limits == null ? null : limits.get(role);
        if (raw == null) {
            throw ApiException.forbidden(ErrorCodes.APPROVAL_AUTHORITY_EXCEEDED, "Role " + role + " cannot approve claims");
        }
        BigDecimal limit = new BigDecimal(raw.toString());
        if (amount.compareTo(limit) > 0) {
            throw ApiException.forbidden(ErrorCodes.APPROVAL_AUTHORITY_EXCEEDED,
                    "Amount exceeds your limit of " + currency + " " + limit.stripTrailingZeros().toPlainString());
        }
    }
}
