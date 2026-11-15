package com.claims.platform.claims.platform.repository.impl;

import com.claims.platform.claims.platform.common.JdbcSupport;
import com.claims.platform.claims.platform.repository.IdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcIdempotencyRepository implements IdempotencyRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final ObjectMapper canonical;

    public JdbcIdempotencyRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.canonical = json.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Override
    public String fingerprint(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(JdbcSupport.json(canonical, request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public <T> Optional<Stored<T>> find(String callerId, UUID key, Class<T> responseType) {
        return jdbc.sql("""
                        SELECT request_hash, response_status, response_body::text AS body
                        FROM platform.idempotency_key WHERE caller_id = :caller AND idempotency_key = :key
                        """)
                .param("caller", callerId).param("key", key)
                .query((rs, n) -> new Stored<>(rs.getString("request_hash"), rs.getInt("response_status"),
                        JdbcSupport.fromJson(json, rs.getString("body"), responseType)))
                .optional();
    }

    @Override
    public void save(String callerId, UUID key, String fingerprint, int status, Object response) {
        try {
            jdbc.sql("""
                            INSERT INTO platform.idempotency_key (caller_id, idempotency_key, request_hash, response_status,
                                response_body)
                            VALUES (:caller, :key, :hash, :status, :body::jsonb)
                            """)
                    .param("caller", callerId).param("key", key).param("hash", fingerprint).param("status", status)
                    .param("body", JdbcSupport.json(json, response))
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new KeyAlreadyUsedException(key);
        }
    }
}
