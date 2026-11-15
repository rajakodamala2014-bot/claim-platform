package com.claims.platform.claims.core.service.impl;

import com.claims.platform.claims.core.repository.DocumentRepository;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.Document;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.service.DocumentService;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentServiceImpl implements DocumentService {
    private static final int MAX_PER_REQUEST = 20;

    private final DocumentRepository documents;
    private final Clock clock;

    public DocumentServiceImpl(DocumentRepository documents, Clock clock) {
        this.documents = documents;
        this.clock = clock;
    }

    @Override
    public List<UUID> attachDocumentsToClaim(List<UUID> uploadIds, ClaimId claimId, String ownerId) {
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

    @Override
    public Document getPendingUpload(UUID uploadId) {
        return documents.findById(uploadId)
                .filter(d -> d.status() == Document.Status.PENDING_UPLOAD)
                .orElseThrow(() -> new DomainException(ErrorCode.UPLOAD_NOT_FOUND, "Upload " + uploadId + " not found"));
    }

    @Override
    public Document completeUpload(UUID uploadId, long receivedBytes) {
        Document uploaded = getPendingUpload(uploadId).uploaded(receivedBytes, clock.instant());
        documents.save(uploaded);
        return uploaded;
    }
}
