package com.claims.platform.identity.web;

import com.claims.platform.common.money.Money;

import java.time.LocalDate;
import java.util.List;

public record PolicyResponse(
        String policyId,
        String policyNumber,
        String market,
        String product,
        String status,
        LocalDate coverageStart,
        LocalDate coverageEnd,
        Money excess,
        Money limit,
        List<String> coveredPerils,
        Insured insured
) {
    public record Insured(String userId, String name) {
    }
}
