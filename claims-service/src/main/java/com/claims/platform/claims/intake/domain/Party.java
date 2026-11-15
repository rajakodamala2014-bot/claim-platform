package com.claims.platform.claims.intake.domain;

import com.claims.platform.claims.intake.domain.MotorIncident.ThirdParty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A person involved in the claim (intake.party). */
public record Party(Role role, String name, String phone, String email, String vehicleRegistration, String insurer) {
    public enum Role { CLAIMANT, DRIVER, THIRD_PARTY, WITNESS }

    public Party {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(name, "name");
    }

    /** Parties implied by an FNOL: the claimant, plus driver and other parties for motor. */
    public static List<Party> fromFnol(String claimantName, String phone, String email, IncidentDetails incident) {
        List<Party> parties = new ArrayList<>();
        parties.add(new Party(Role.CLAIMANT, claimantName, phone, email, null, null));
        if (incident instanceof MotorIncident motor) {
            if (!motor.driverName().equalsIgnoreCase(claimantName)) {
                parties.add(new Party(Role.DRIVER, motor.driverName(), null, null, motor.vehicleRegistration(), null));
            }
            for (ThirdParty other : motor.thirdParties()) {
                parties.add(new Party(Role.THIRD_PARTY, other.name(), other.phone(), null, other.vehicleRegistration(),
                        other.insurer()));
            }
        }
        return List.copyOf(parties);
    }
}
