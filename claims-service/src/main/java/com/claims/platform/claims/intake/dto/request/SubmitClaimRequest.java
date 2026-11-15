package com.claims.platform.claims.intake.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "First notification of loss by a customer")
public record SubmitClaimRequest(
        @NotBlank @Size(max = 40) String policyNumber,
        @NotNull LineOfBusiness lineOfBusiness,
        @NotNull Instant incidentAt,
        @NotNull @Valid IncidentRequest incident,
        @Valid MoneyRequest claimedAmount,
        @Size(max = 20) List<UUID> uploadIds,
        @Valid ContactRequest contact
) {
}
