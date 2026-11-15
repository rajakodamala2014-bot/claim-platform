package com.claims.platform.claims.core.service;

import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.Document;
import java.util.List;
import java.util.UUID;

/** Uploaded documents: confirming that the bytes arrived and linking completed uploads to a claim. */
public interface DocumentService {
    /** Links completed uploads to a claim (FNOL, information responses). Must run inside the caller's transaction. */
    List<UUID> attachDocumentsToClaim(List<UUID> uploadIds, ClaimId claimId, String ownerId);

    /** Storage confirms the bytes arrived (local storage adapter calls this; an S3 adapter would use an event). */
    Document getPendingUpload(UUID uploadId);

    /** Storage confirms the bytes arrived (local storage adapter calls this; an S3 adapter would use an event). */
    Document completeUpload(UUID uploadId, long receivedBytes);
}
