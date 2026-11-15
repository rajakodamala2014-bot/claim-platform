package com.claims.platform.claims.core.application;

import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.Document;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Links completed uploads to a claim (FNOL, information responses). Must run inside the caller's transaction. */
public class AttachDocuments {
    private static final int MAX_PER_REQUEST = 20;
    private final DocumentRepository documents;

    public AttachDocuments(DocumentRepository documents) {
        this.documents = documents;
    }

    public List<UUID> attach(List<UUID> uploadIds, ClaimId claimId, String ownerId) {
        if (uploadIds == null || uploadIds.isEmpty()) {
            return List.of();
        }
        List<UUID> unique = List.copyOf(new LinkedHashSet<>(uploadIds));
        if (unique.size() > MAX_PER_REQUEST) {
            throw new DomainException(ErrorCode.UPLOAD_REJECTED, "At most " + MAX_PER_REQUEST + " documents per request");
        }
        for (UUID id : unique) {
            Document document = documents.findById(id)
                    .orElseThrow(() -> new DomainException(ErrorCode.UPLOAD_NOT_FOUND, "Upload " + id + " not found"));
            documents.save(document.attachTo(claimId, ownerId));
        }
        return unique;
    }
}
