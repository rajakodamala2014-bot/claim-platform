package com.claims.platform.claims.intake.domain;

import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.core.domain.ValidationException;
import com.claims.platform.claims.core.domain.ValidationException.FieldViolation;

import java.util.ArrayList;
import java.util.List;

/** What happened, specific to the line of business. Stored as intake.incident.details (JSONB). */
public sealed interface IncidentDetails permits MotorIncident, PropertyIncident {

    LineOfBusiness lineOfBusiness();

    String peril();

    String description();

    String location();

    /** Field-level problems; empty when valid. Field names are relative to {@code incident}. */
    List<FieldViolation> violations();

    default void validate() {
        List<FieldViolation> violations = violations();
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    /** Small helper shared by the implementations. */
    final class Checks {
        private final List<FieldViolation> violations = new ArrayList<>();

        public Checks required(String field, String value, int maxLength) {
            if (value == null || value.isBlank()) {
                violations.add(new FieldViolation(field, "is required"));
            } else if (value.length() > maxLength) {
                violations.add(new FieldViolation(field, "must be at most " + maxLength + " characters"));
            }
            return this;
        }

        public Checks optional(String field, String value, int maxLength) {
            if (value != null && value.length() > maxLength) {
                violations.add(new FieldViolation(field, "must be at most " + maxLength + " characters"));
            }
            return this;
        }

        public Checks oneOf(String field, String value, java.util.Set<String> allowed) {
            if (value == null || !allowed.contains(value)) {
                violations.add(new FieldViolation(field, "must be one of " + new java.util.TreeSet<>(allowed)));
            }
            return this;
        }

        public Checks check(boolean ok, String field, String message) {
            if (!ok) {
                violations.add(new FieldViolation(field, message));
            }
            return this;
        }

        public List<FieldViolation> result() {
            return List.copyOf(violations);
        }
    }
}
