package com.claims.platform.claims.intake.service;

import com.claims.platform.claims.core.service.DocumentStorageService.UploadTarget;
import java.util.UUID;

/** Registering document uploads (C2). */
public interface DocumentUploadService {
    record RequestedUpload(UUID uploadId, UploadTarget upload) {
    }

    /** C2: registers an upload and returns a pre-signed URL; the app sends the bytes straight to storage. */
    RequestedUpload requestUploadUrl(String ownerId, String fileName, String contentType, long sizeBytes);
}
