package com.claims.platform.claims.intake.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Schema(description = "A claim registered by staff on behalf of a customer")
public record RegisterClaimRequest(
        @NotBlank @Size(max = 100) String claimantId,
        @NotBlank @Size(max = 200) String claimantName,
        @NotBlank @Size(max = 40) String policyNumber,
        @NotNull LineOfBusiness lineOfBusiness,
        @NotNull Instant incidentAt,
        @NotNull @Valid IncidentRequest incident,
        @Valid MoneyRequest claimedAmount,
        @Valid ContactRequest contact
) {
}
