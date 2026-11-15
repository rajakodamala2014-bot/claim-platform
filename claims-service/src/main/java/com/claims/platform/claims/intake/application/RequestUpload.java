package com.claims.platform.claims.intake.application;

import com.claims.platform.claims.core.application.DocumentRepository;
import com.claims.platform.claims.core.application.DocumentStorage;
import com.claims.platform.claims.core.application.DocumentStorage.UploadTarget;
import com.claims.platform.claims.core.domain.Document;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** C2: registers an upload and returns a pre-signed URL; the app sends the bytes straight to storage. */
public class RequestUpload {
    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final Clock clock;

    public RequestUpload(DocumentRepository documents, DocumentStorage storage, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.clock = clock;
    }

    public record Result(UUID uploadId, UploadTarget upload) {
    }

    public Result request(String ownerId, String fileName, String contentType, long sizeBytes) {
        Instant now = clock.instant();
        Document document = Document.requested(UUID.randomUUID(), ownerId, fileName, contentType, sizeBytes, now);
        documents.save(document);
        return new Result(document.id(), storage.prepareUpload(document, now));
    }
}
