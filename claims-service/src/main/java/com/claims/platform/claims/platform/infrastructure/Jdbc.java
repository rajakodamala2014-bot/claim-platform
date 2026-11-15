package com.claims.platform.claims.platform.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small JDBC conversions shared by the repositories (pgjdbc speaks OffsetDateTime, not Instant). */
public final class Jdbc {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private Jdbc() {
    }

    public static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public static String trimmed(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : value.trim();
    }

    public static List<String> strings(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    public static List<UUID> uuids(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        return array == null ? List.of() : Arrays.asList((UUID[]) array.getArray());
    }

    public static String json(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise " + value.getClass().getSimpleName(), ex);
        }
    }

    public static Map<String, Object> jsonMap(ObjectMapper mapper, String json) {
        try {
            return mapper.readValue(json, MAP);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read JSON column", ex);
        }
    }

    public static <T> T fromJson(ObjectMapper mapper, String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read JSON as " + type.getSimpleName(), ex);
        }
    }
}
