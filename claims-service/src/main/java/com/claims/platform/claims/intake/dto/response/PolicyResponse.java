package com.claims.platform.claims.intake.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.dto.response.MoneyResponse;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "A policy the customer can claim on")
public record PolicyResponse(String policyNumber, String product, String lineOfBusiness, String market,
                         LocalDate coverageStart, LocalDate coverageEnd, MoneyResponse excess, List<String> coveredPerils) {
}
