package com.claims.platform.web;

import com.claims.platform.common.error.ErrorCodes;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class IdempotencyExecutor {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyExecutor(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void requireKey(UUID key) {
        if (key == null) {
            throw ApiException.badRequest(ErrorCodes.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key header is required");
        }
    }

    public <T> ResponseEntity<T> execute(UUID key, Object request, HttpStatus success, Class<T> type, Supplier<T> action) {
        requireKey(key);
        JavaType javaType = objectMapper.getTypeFactory().constructType(type);
        return execute(key, request, success, javaType, action);
    }

    public <T> ResponseEntity<T> execute(UUID key, Object request, HttpStatus success, JavaType type, Supplier<T> action) {
        requireKey(key);
        String hash = hash(request);
        Stored existing = find(key);
        if (existing != null) {
            return replay(existing, hash, type);
        }
        T body = action.get();
        try {
            insert(key, hash, success.value(), body);
        } catch (DataIntegrityViolationException ex) {
            Stored raced = find(key);
            if (raced != null) {
                return replay(raced, hash, type);
            }
            throw ex;
        }
        return ResponseEntity.status(success).body(body);
    }

    private <T> ResponseEntity<T> replay(Stored existing, String hash, JavaType type) {
        if (!existing.hash.equals(hash)) {
            throw ApiException.conflict(ErrorCodes.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key was already used with a different request");
        }
        try {
            T body = objectMapper.readValue(existing.body, type);
            return ResponseEntity.status(existing.status).body(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored idempotent response could not be read", ex);
        }
    }

    private void insert(UUID key, String hash, int status, Object body) {
        try {
            String payload = objectMapper.writeValueAsString(new StoredResponse(status, objectMapper.valueToTree(body)));
            jdbcTemplate.update(
                    "insert into idempotency_key (key, request_hash, response) values (?, ?, ?::jsonb)",
                    key, hash, payload);
        } catch (DataIntegrityViolationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not store idempotency key", ex);
        }
    }

    private Stored find(UUID key) {
        return jdbcTemplate.query(
                "select request_hash, response::text from idempotency_key where key = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    try {
                        JsonNode node = objectMapper.readTree(rs.getString(2));
                        return new Stored(rs.getString(1), node.get("status").asInt(), node.get("body"));
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                },
                key);
    }

    private String hash(Object request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash request", ex);
        }
    }

    private record Stored(String hash, int status, JsonNode body) {
    }

    private record StoredResponse(int status, JsonNode body) {
    }
}
