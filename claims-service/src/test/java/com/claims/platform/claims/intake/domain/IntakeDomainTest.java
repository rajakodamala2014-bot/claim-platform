package com.claims.platform.claims.intake.domain;

import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.core.domain.ValidationException.FieldViolation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntakeDomainTest {

    private static MotorIncident motor(String peril, List<MotorIncident.ThirdParty> others, String policeReport) {
        return new MotorIncident(peril, "Rear-ended", "Orchard Rd", "SBA1234X", "Priya", "Bumper", policeReport, others);
    }

    @Test
    void validMotorIncidentHasNoViolations() {
        assertThat(motor("THEFT", List.of(), null).violations()).isEmpty();
        assertThat(motor("COLLISION", List.of(new MotorIncident.ThirdParty("John", null, null, null)), null).violations()).isEmpty();
        assertThat(motor("COLLISION", List.of(), "PR-1").violations()).isEmpty();
    }

    @Test
    void collisionNeedsOtherPartyOrPoliceReport() {
        assertThat(motor("COLLISION", List.of(), " ").violations()).extracting(FieldViolation::field)
                .containsExactly("incident.thirdParties");
    }

    @Test
    void motorFieldsAreRequiredAndBounded() {
        MotorIncident bad = new MotorIncident("SPACESHIP", " ", null, "X".repeat(21), null, "",
                null, List.of(new MotorIncident.ThirdParty(" ", null, null, null)));

        assertThat(bad.violations()).extracting(FieldViolation::field).containsExactlyInAnyOrder(
                "incident.peril", "incident.description", "incident.location", "incident.vehicleRegistration",
                "incident.driverName", "incident.damageDescription", "incident.thirdParties[0].name");
    }

    @Test
    void propertyNeedsAddressPerilAndDamagedItems() {
        PropertyIncident ok = new PropertyIncident("FLOOD", "Pipe burst", null, "10 Marina Blvd",
                List.of(new PropertyIncident.DamagedItem("Sofa", 150_000L)));
        PropertyIncident bad = new PropertyIncident("COLLISION", "x", null, " ",
                List.of(new PropertyIncident.DamagedItem("", -1L)));

        assertThat(ok.violations()).isEmpty();
        assertThat(ok.lineOfBusiness()).isEqualTo(LineOfBusiness.PROPERTY);
        assertThat(bad.violations()).extracting(FieldViolation::field).containsExactlyInAnyOrder("incident.peril",
                "incident.propertyAddress", "incident.damagedItems[0].description",
                "incident.damagedItems[0].estimatedValueMinor");
        assertThat(new PropertyIncident("FIRE", "x", null, "addr", List.of()).violations())
                .extracting(FieldViolation::field).containsExactly("incident.damagedItems");
    }

    @Test
    void policyMustMatchLineAndBeInForceOnTheIncidentDay() {
        PolicySnapshot policy = new PolicySnapshot("P1", "c", "C", MarketCode.of("SG"), LineOfBusiness.MOTOR, "Motor",
                "ACTIVE", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "SGD", null, null, List.of());

        assertThatCode(() -> policy.requireClaimable(LineOfBusiness.MOTOR, LocalDate.of(2026, 1, 1))).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireClaimable(LineOfBusiness.MOTOR, LocalDate.of(2026, 12, 31))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireClaimable(LineOfBusiness.MOTOR, LocalDate.of(2027, 1, 1)))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.POLICY_NOT_IN_FORCE);
        assertThatThrownBy(() -> policy.requireClaimable(LineOfBusiness.PROPERTY, LocalDate.of(2026, 6, 1)))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void partiesComeFromTheFnol() {
        List<Party> parties = Party.fromFnol("Priya Nair", "+65 9123 4567", "p@example.com",
                new MotorIncident("COLLISION", "x", "y", "SBA1", "Ravi Nair", "z", null,
                        List.of(new MotorIncident.ThirdParty("John", "SGX1", "Income", null))));

        assertThat(parties).extracting(Party::role).containsExactly(Party.Role.CLAIMANT, Party.Role.DRIVER, Party.Role.THIRD_PARTY);
    }
}
