package com.claims.platform.claims.intake.service.impl;

import com.claims.platform.claims.core.repository.DocumentRepository;
import com.claims.platform.claims.core.service.DocumentStorageService;
import com.claims.platform.claims.core.entity.Document;
import com.claims.platform.claims.intake.service.DocumentUploadService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentUploadServiceImpl implements DocumentUploadService {
    private final DocumentRepository documents;
    private final DocumentStorageService storage;
    private final Clock clock;

    public DocumentUploadServiceImpl(DocumentRepository documents, DocumentStorageService storage, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.clock = clock;
    }

    @Override
    public RequestedUpload requestUploadUrl(String ownerId, String fileName, String contentType, long sizeBytes) {
        Instant now = clock.instant();
        Document document = Document.requested(UUID.randomUUID(), ownerId, fileName, contentType, sizeBytes, now);
        documents.save(document);
        return new RequestedUpload(document.id(), storage.prepareUpload(document, now));
    }
}
