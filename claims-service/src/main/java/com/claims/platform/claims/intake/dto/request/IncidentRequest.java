package com.claims.platform.claims.intake.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.intake.entity.IncidentDetails;
import com.claims.platform.claims.intake.entity.MotorIncident;
import com.claims.platform.claims.intake.entity.PropertyIncident;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "What happened; the fields that apply depend on the line of business")
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
    public IncidentDetails toIncidentDetails(LineOfBusiness lob) {
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
