package com.claims.platform.claims.core.infrastructure.storage;

import com.claims.platform.claims.core.application.CompleteUpload;
import com.claims.platform.claims.core.domain.Document;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

/**
 * Part of the local storage adapter, standing in for S3: accepts the PUT of a pre-signed upload URL.
 * Authorised by the URL signature (not a bearer token), exactly like an S3 pre-signed URL; the path is
 * permitted in the security config for that reason.
 */
@RestController
public class LocalObjectStoreController {
    public static final String PATH = "/claimant/v1/uploads/{uploadId}/content";

    private final LocalDocumentStorage storage;
    private final UploadUrlSigner signer;
    private final CompleteUpload completeUpload;
    private final Clock clock;

    public LocalObjectStoreController(LocalDocumentStorage storage, UploadUrlSigner signer, CompleteUpload completeUpload,
                                      Clock clock) {
        this.storage = storage;
        this.signer = signer;
        this.completeUpload = completeUpload;
        this.clock = clock;
    }

    @PutMapping(PATH)
    public ResponseEntity<Void> put(@PathVariable UUID uploadId,
                                    @RequestParam long expires,
                                    @RequestParam String signature,
                                    @RequestHeader(value = "Content-Type", required = false) String contentType,
                                    HttpServletRequest request) throws IOException {
        Document pending = completeUpload.pending(uploadId);
        if (!signer.verify(uploadId, expires, pending.contentType(), pending.sizeBytes(), signature, clock.instant())) {
            throw new DomainException(ErrorCode.UPLOAD_REJECTED, "Upload URL is invalid or has expired");
        }
        if (contentType == null || !contentType.startsWith(pending.contentType())) {
            throw new DomainException(ErrorCode.UPLOAD_REJECTED, "Content-Type must be " + pending.contentType());
        }
        long received = storage.write(uploadId, request.getInputStream(), pending.sizeBytes());
        completeUpload.complete(uploadId, received);
        return ResponseEntity.ok().build();
    }
}
