package com.claims.platform.claims.core.repository.impl;

import com.claims.platform.claims.core.repository.DocumentRepository;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.Document;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.claims.platform.claims.platform.common.JdbcSupport.instant;
import static com.claims.platform.claims.platform.common.JdbcSupport.ts;

@Repository
public class JdbcDocumentRepository implements DocumentRepository {
    private final JdbcClient jdbc;

    public JdbcDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Document d) {
        jdbc.sql("""
                        INSERT INTO core.document (id, owner_id, claim_id, market_code, file_name, content_type, size_bytes,
                            status, storage_key, uploaded_at, created_at)
                        VALUES (:id, :owner, :claim,
                            (SELECT market_code FROM core.claim WHERE id = :claim),
                            :name, :type, :size, :status, :key, :uploadedAt, :createdAt)
                        ON CONFLICT (id) DO UPDATE SET claim_id = EXCLUDED.claim_id, market_code = EXCLUDED.market_code,
                            size_bytes = EXCLUDED.size_bytes, status = EXCLUDED.status, uploaded_at = EXCLUDED.uploaded_at,
                            updated_at = now()
                        """)
                .param("id", d.id())
                .param("owner", d.ownerId())
                .param("claim", d.claimId() == null ? null : d.claimId().value())
                .param("name", d.fileName())
                .param("type", d.contentType())
                .param("size", d.sizeBytes())
                .param("status", d.status().name())
                .param("key", d.id().toString())
                .param("uploadedAt", ts(d.uploadedAt()))
                .param("createdAt", ts(d.createdAt()))
                .update();
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return jdbc.sql("SELECT * FROM core.document WHERE id = :id").param("id", id).query((rs, n) -> map(rs)).optional();
    }

    @Override
    public List<Document> findByClaimId(ClaimId claimId) {
        return jdbc.sql("SELECT * FROM core.document WHERE claim_id = :claim ORDER BY created_at, id")
                .param("claim", claimId.value()).query((rs, n) -> map(rs)).list();
    }

    private static Document map(ResultSet rs) throws SQLException {
        UUID claim = rs.getObject("claim_id", UUID.class);
        return new Document(rs.getObject("id", UUID.class), rs.getString("owner_id"),
                claim == null ? null : new ClaimId(claim), rs.getString("file_name"), rs.getString("content_type"),
                rs.getLong("size_bytes"), Document.Status.valueOf(rs.getString("status")), instant(rs, "created_at"),
                instant(rs, "uploaded_at"));
    }
}
