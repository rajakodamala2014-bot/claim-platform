package com.claims.platform.claims.intake.domain;

import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.core.domain.ValidationException.FieldViolation;

import java.util.List;
import java.util.Set;

/** Property: address, peril (fire, flood, theft...) and damaged items. */
public record PropertyIncident(
        String peril,
        String description,
        String location,
        String propertyAddress,
        List<DamagedItem> damagedItems
) implements IncidentDetails {
    public static final Set<String> PERILS = Set.of("FIRE", "FLOOD", "THEFT", "WATER_DAMAGE", "STORM");

    public PropertyIncident {
        damagedItems = damagedItems == null ? List.of() : List.copyOf(damagedItems);
    }

    @Override
    public LineOfBusiness lineOfBusiness() {
        return LineOfBusiness.PROPERTY;
    }

    @Override
    public List<FieldViolation> violations() {
        Checks checks = new Checks()
                .oneOf("incident.peril", peril, PERILS)
                .required("incident.description", description, 2000)
                .optional("incident.location", location, 300)
                .required("incident.propertyAddress", propertyAddress, 300)
                .check(!damagedItems.isEmpty(), "incident.damagedItems", "list at least one damaged item")
                .check(damagedItems.size() <= 50, "incident.damagedItems", "at most 50 items");
        for (int i = 0; i < damagedItems.size(); i++) {
            DamagedItem item = damagedItems.get(i);
            checks.required("incident.damagedItems[" + i + "].description", item.description(), 300)
                    .check(item.estimatedValueMinor() == null || item.estimatedValueMinor() >= 0,
                            "incident.damagedItems[" + i + "].estimatedValueMinor", "cannot be negative");
        }
        return checks.result();
    }

    /** @param estimatedValueMinor customer's estimate in the claim currency's minor unit, optional */
    public record DamagedItem(String description, Long estimatedValueMinor) {
    }
}
