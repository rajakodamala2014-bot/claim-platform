package com.claims.platform.notification.entity;

import java.util.UUID;

/** Minimal claim facts, projected from claims events, so Notifications never calls Claims. */
public record ClaimContact(UUID claimId, String claimNumber, String claimantId, String market, String lineOfBusiness,
                           String handlerId, String teamId) {
    public ClaimContact assignedTo(String handler, String team) {
        return new ClaimContact(claimId, claimNumber, claimantId, market, lineOfBusiness, handler, team);
    }
}
