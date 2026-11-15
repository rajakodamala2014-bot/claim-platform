package com.claims.platform.claims.core.application;

import com.claims.platform.claims.core.domain.Document;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;

import java.time.Clock;
import java.util.UUID;

/** Storage confirms the bytes arrived (local storage adapter calls this; an S3 adapter would use an event). */
public class CompleteUpload {
    private final DocumentRepository documents;
    private final Clock clock;

    public CompleteUpload(DocumentRepository documents, Clock clock) {
        this.documents = documents;
        this.clock = clock;
    }

    public Document pending(UUID uploadId) {
        return documents.findById(uploadId)
                .filter(d -> d.status() == Document.Status.PENDING_UPLOAD)
                .orElseThrow(() -> new DomainException(ErrorCode.UPLOAD_NOT_FOUND, "Upload " + uploadId + " not found"));
    }

    public Document complete(UUID uploadId, long receivedBytes) {
        Document uploaded = pending(uploadId).uploaded(receivedBytes, clock.instant());
        documents.save(uploaded);
        return uploaded;
    }
}
