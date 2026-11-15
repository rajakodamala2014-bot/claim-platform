package com.claims.platform.claims.intake.entity;

import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.core.exception.ValidationException.FieldViolation;

import java.util.List;
import java.util.Set;

/** Motor: vehicle, driver, other parties and damage. */
public record MotorIncident(
        String peril,
        String description,
        String location,
        String vehicleRegistration,
        String driverName,
        String damageDescription,
        String policeReportNumber,
        List<ThirdParty> thirdParties
) implements IncidentDetails {
    public static final Set<String> PERILS = Set.of("COLLISION", "THEFT", "FIRE", "FLOOD", "VANDALISM", "WINDSCREEN");

    public MotorIncident {
        thirdParties = thirdParties == null ? List.of() : List.copyOf(thirdParties);
    }

    @Override
    public LineOfBusiness lineOfBusiness() {
        return LineOfBusiness.MOTOR;
    }

    @Override
    public List<FieldViolation> violations() {
        Checks checks = new Checks()
                .oneOf("incident.peril", peril, PERILS)
                .required("incident.description", description, 2000)
                .required("incident.location", location, 300)
                .required("incident.vehicleRegistration", vehicleRegistration, 20)
                .required("incident.driverName", driverName, 200)
                .required("incident.damageDescription", damageDescription, 2000)
                .optional("incident.policeReportNumber", policeReportNumber, 40)
                .check(thirdParties.size() <= 10, "incident.thirdParties", "at most 10 other parties");
        if ("COLLISION".equals(peril)) {
            checks.check(!thirdParties.isEmpty() || !isBlankOrNull(policeReportNumber),
                    "incident.thirdParties", "a collision needs the other party's details or a police report number");
        }
        for (int i = 0; i < thirdParties.size(); i++) {
            ThirdParty party = thirdParties.get(i);
            checks.required("incident.thirdParties[" + i + "].name", party.name(), 200)
                    .optional("incident.thirdParties[" + i + "].vehicleRegistration", party.vehicleRegistration(), 20)
                    .optional("incident.thirdParties[" + i + "].insurer", party.insurer(), 100)
                    .optional("incident.thirdParties[" + i + "].phone", party.phone(), 40);
        }
        return checks.result();
    }

    private static boolean isBlankOrNull(String value) {
        return value == null || value.isBlank();
    }

    public record ThirdParty(String name, String vehicleRegistration, String insurer, String phone) {
    }
}
