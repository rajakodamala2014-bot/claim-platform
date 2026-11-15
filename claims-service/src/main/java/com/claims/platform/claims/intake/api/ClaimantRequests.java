package com.claims.platform.claims.intake.api;

import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.core.domain.ValidationException;
import com.claims.platform.claims.intake.domain.IncidentDetails;
import com.claims.platform.claims.intake.domain.MotorIncident;
import com.claims.platform.claims.intake.domain.PropertyIncident;
import com.claims.platform.common.money.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request bodies of the claimant surface. Shape is checked here; business rules in the domain. */
public final class ClaimantRequests {
    private ClaimantRequests() {
    }

    public record MoneyRequest(@NotBlank String amount, @NotBlank @Size(min = 3, max = 3) String currency) {
        Money toMoney(String field) {
            try {
                return Money.of(amount, currency);
            } catch (IllegalArgumentException ex) {
                throw ValidationException.of(field, ex.getMessage());
            }
        }
    }

    public record ContactRequest(@Size(max = 40) String phone, @Email @Size(max = 200) String email) {
    }

    public record ThirdPartyRequest(String name, String vehicleRegistration, String insurer, String phone) {
    }

    public record DamagedItemRequest(String description, MoneyRequest estimatedValue) {
    }

    /**
     * One shape for both lines of business; fields that don't apply to the claim's line of business are ignored.
     * MOTOR: vehicleRegistration, driverName, damageDescription, policeReportNumber, thirdParties.
     * PROPERTY: propertyAddress, damagedItems.
     */
    public record IncidentRequest(
            @NotBlank String peril,
            @NotBlank @Size(max = 2000) String description,
            @Size(max = 300) String location,
            String vehicleRegistration,
            String driverName,
            String damageDescription,
            String policeReportNumber,
            @Size(max = 10) List<ThirdPartyRequest> thirdParties,
            String propertyAddress,
            @Size(max = 50) List<DamagedItemRequest> damagedItems
    ) {
        IncidentDetails toDomain(LineOfBusiness lob) {
            return switch (lob) {
                case MOTOR -> new MotorIncident(peril, description, location, vehicleRegistration, driverName,
                        damageDescription, policeReportNumber, thirdParties == null ? List.of() : thirdParties.stream()
                        .map(t -> new MotorIncident.ThirdParty(t.name(), t.vehicleRegistration(), t.insurer(), t.phone()))
                        .toList());
                case PROPERTY -> new PropertyIncident(peril, description, location, propertyAddress,
                        damagedItems == null ? List.of() : damagedItems.stream()
                                .map(i -> new PropertyIncident.DamagedItem(i.description(),
                                        i.estimatedValue() == null ? null
                                                : i.estimatedValue().toMoney("incident.damagedItems.estimatedValue").amountMinor()))
                                .toList());
            };
        }
    }

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

    public record UploadRequest(@NotBlank @Size(max = 255) String fileName, @NotBlank String contentType,
                                @Positive long sizeBytes) {
    }

    public record WithdrawRequest(@Size(max = 1000) String reason) {
    }

    public record AppealRequest(@NotBlank @Size(max = 2000) String reason) {
    }
}
