package com.claims.platform.claims.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A handler's request for documents/answers from the claimant. One may be open at a time. */
public record InformationRequest(
        UUID id,
        List<String> items,
        Instant dueAt,
        String requestedBy,
        Instant requestedAt,
        Status status,
        String answer,
        List<UUID> documentIds,
        Instant closedAt
) {
    public enum Status { OPEN, ANSWERED, EXPIRED, CANCELLED }

    public InformationRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(status, "status");
        items = List.copyOf(items == null ? List.of() : items);
        documentIds = List.copyOf(documentIds == null ? List.of() : documentIds);
    }

    InformationRequest answered(String answer, List<UUID> documentIds, Instant at) {
        return new InformationRequest(id, items, dueAt, requestedBy, requestedAt, Status.ANSWERED, answer,
                documentIds, at);
    }

    InformationRequest expired(Instant at) {
        return new InformationRequest(id, items, dueAt, requestedBy, requestedAt, Status.EXPIRED, null, List.of(), at);
    }

    /** The claim was withdrawn while the request was open. */
    InformationRequest cancelled(Instant at) {
        return new InformationRequest(id, items, dueAt, requestedBy, requestedAt, Status.CANCELLED, null, List.of(), at);
    }
}
