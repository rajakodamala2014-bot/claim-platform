package com.claims.platform.claims.intake.infrastructure;

import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.intake.application.IntakeRecords;
import com.claims.platform.claims.intake.domain.IncidentDetails;
import com.claims.platform.claims.intake.domain.MotorIncident;
import com.claims.platform.claims.intake.domain.Party;
import com.claims.platform.claims.intake.domain.PolicySnapshot;
import com.claims.platform.claims.intake.domain.PropertyIncident;
import com.claims.platform.claims.platform.infrastructure.Jdbc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.claims.platform.claims.platform.infrastructure.Jdbc.ts;

@Repository
public class JdbcIntakeRecords implements IntakeRecords {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcIntakeRecords(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void saveIncident(ClaimId claimId, MarketCode market, Instant occurredAt, IncidentDetails incident) {
        jdbc.sql("""
                        INSERT INTO intake.incident (claim_id, market_code, line_of_business, peril, occurred_at, location,
                            description, details)
                        VALUES (:claim, :market, :lob, :peril, :at, :location, :description, :details::jsonb)
                        """)
                .param("claim", claimId.value()).param("market", market.value())
                .param("lob", incident.lineOfBusiness().name()).param("peril", incident.peril())
                .param("at", ts(occurredAt)).param("location", incident.location())
                .param("description", incident.description()).param("details", Jdbc.json(json, incident))
                .update();
    }

    @Override
    public void saveParties(ClaimId claimId, MarketCode market, List<Party> parties) {
        for (Party p : parties) {
            jdbc.sql("""
                            INSERT INTO intake.party (id, claim_id, market_code, party_role, name, phone, email,
                                vehicle_registration, insurer)
                            VALUES (:id, :claim, :market, :role, :name, :phone, :email, :vehicle, :insurer)
                            """)
                    .param("id", UUID.randomUUID()).param("claim", claimId.value()).param("market", market.value())
                    .param("role", p.role().name()).param("name", p.name()).param("phone", p.phone())
                    .param("email", p.email()).param("vehicle", p.vehicleRegistration()).param("insurer", p.insurer())
                    .update();
        }
    }

    @Override
    public void savePolicySnapshot(ClaimId claimId, PolicySnapshot p, Instant capturedAt) {
        jdbc.sql("""
                        INSERT INTO intake.policy_snapshot (claim_id, market_code, policy_number, holder_id, holder_name,
                            line_of_business, product, policy_status, coverage_start, coverage_end, currency,
                            sum_insured_minor, excess_minor, covered_perils, captured_at)
                        VALUES (:claim, :market, :number, :holder, :holderName, :lob, :product, :status, :start, :end,
                            :currency, :sumInsured, :excess, :perils, :capturedAt)
                        ON CONFLICT (claim_id) DO UPDATE SET policy_status = EXCLUDED.policy_status,
                            covered_perils = EXCLUDED.covered_perils, captured_at = EXCLUDED.captured_at, updated_at = now()
                        """)
                .param("claim", claimId.value()).param("market", p.market().value()).param("number", p.policyNumber())
                .param("holder", p.holderId()).param("holderName", p.holderName())
                .param("lob", p.lineOfBusiness().name()).param("product", p.product()).param("status", p.status())
                .param("start", p.coverageStart()).param("end", p.coverageEnd()).param("currency", p.currency())
                .param("sumInsured", p.sumInsuredMinor()).param("excess", p.excessMinor())
                .param("perils", new SqlArrayValue("text", p.coveredPerils().toArray()))
                .param("capturedAt", ts(capturedAt))
                .update();
    }

    @Override
    public Optional<IncidentDetails> findIncident(ClaimId claimId) {
        return jdbc.sql("SELECT line_of_business, details::text AS details FROM intake.incident WHERE claim_id = :claim")
                .param("claim", claimId.value())
                .query((rs, n) -> LineOfBusiness.valueOf(rs.getString("line_of_business")) == LineOfBusiness.MOTOR
                        ? (IncidentDetails) Jdbc.fromJson(json, rs.getString("details"), MotorIncident.class)
                        : Jdbc.fromJson(json, rs.getString("details"), PropertyIncident.class))
                .optional();
    }

    @Override
    public Optional<PolicySnapshot> findPolicySnapshot(ClaimId claimId) {
        return jdbc.sql("SELECT * FROM intake.policy_snapshot WHERE claim_id = :claim")
                .param("claim", claimId.value())
                .query((rs, n) -> new PolicySnapshot(rs.getString("policy_number"), rs.getString("holder_id"),
                        rs.getString("holder_name"), MarketCode.of(rs.getString("market_code")),
                        LineOfBusiness.valueOf(rs.getString("line_of_business")), rs.getString("product"),
                        rs.getString("policy_status"), rs.getObject("coverage_start", LocalDate.class),
                        rs.getObject("coverage_end", LocalDate.class), rs.getString("currency").trim(),
                        Jdbc.nullableLong(rs, "sum_insured_minor"), Jdbc.nullableLong(rs, "excess_minor"),
                        Jdbc.strings(rs, "covered_perils")))
                .optional();
    }

    @Override
    public List<Party> findParties(ClaimId claimId) {
        return jdbc.sql("SELECT * FROM intake.party WHERE claim_id = :claim ORDER BY CASE party_role WHEN 'CLAIMANT' THEN 0 "
                        + "WHEN 'DRIVER' THEN 1 WHEN 'THIRD_PARTY' THEN 2 ELSE 3 END, name, id")
                .param("claim", claimId.value())
                .query((rs, n) -> new Party(Party.Role.valueOf(rs.getString("party_role")), rs.getString("name"),
                        rs.getString("phone"), rs.getString("email"), rs.getString("vehicle_registration"),
                        rs.getString("insurer")))
                .list();
    }
}
