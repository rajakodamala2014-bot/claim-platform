package com.claims.platform.claims.core.common;

import com.claims.platform.claims.core.exception.ValidationException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Keyset (cursor) pagination over (timestamp DESC, id DESC). The cursor is opaque to clients: base64url of
 * {@code <epochMicros>|<uuid>} of the last item returned.
 */
public record PageRequest(Instant afterTimestamp, UUID afterId, int limit) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public static PageRequest of(String cursor, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw ValidationException.of("limit", "must be between 1 and " + MAX_LIMIT);
        }
        if (cursor == null || cursor.isBlank()) {
            return new PageRequest(null, null, size);
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
            long micros = Long.parseLong(parts[0]);
            Instant at = Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
            return new PageRequest(at, UUID.fromString(parts[1]), size);
        } catch (RuntimeException ex) {
            throw ValidationException.of("cursor", "is not a valid cursor");
        }
    }

    public static String cursorOf(Instant timestamp, UUID id) {
        long micros = Math.addExact(Math.multiplyExact(timestamp.getEpochSecond(), 1_000_000L), timestamp.getNano() / 1_000L);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((micros + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    public boolean isFirstPage() {
        return afterTimestamp == null;
    }
}
