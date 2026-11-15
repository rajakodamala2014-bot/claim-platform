package com.claims.platform.insights.common;

import com.claims.platform.insights.entity.ClaimRow;
import com.claims.platform.insights.exception.InsightsException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Opaque keyset cursor of the work queue: the (SLA due date, claim id) of the last row of the previous page. */
public record WorkQueueCursor(Instant afterDue, UUID afterId) {
    private static final WorkQueueCursor START = new WorkQueueCursor(null, null);

    public static String encode(ClaimRow last) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((last.slaDueAt() + "|" + last.claimId()).getBytes(StandardCharsets.UTF_8));
    }

    /** @return the position to continue from; the start of the queue when no cursor was given */
    public static WorkQueueCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return START;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
            return new WorkQueueCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException ex) {
            throw new InsightsException(InsightsException.Code.VALIDATION_FAILED, "cursor is not valid");
        }
    }
}
