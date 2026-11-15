package com.claims.platform.claims.core.repository;

import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.Document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {

    /** Insert or update. */
    void save(Document document);

    Optional<Document> findById(UUID id);

    List<Document> findByClaimId(ClaimId claimId);
}
