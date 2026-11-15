package com.claims.platform.claims.core.application;

import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.Document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {

    /** Insert or update. */
    void save(Document document);

    Optional<Document> findById(UUID id);

    List<Document> findByClaim(ClaimId claimId);
}
