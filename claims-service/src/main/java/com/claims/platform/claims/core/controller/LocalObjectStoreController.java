package com.claims.platform.claims.core.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.claims.platform.claims.core.service.DocumentStorageService;
import com.claims.platform.claims.core.common.UploadUrlSigner;
import com.claims.platform.claims.core.service.DocumentService;
import com.claims.platform.claims.core.entity.Document;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
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
@Tag(name = "Document storage", description = "Receives the bytes of a pre-signed upload (local stand-in for S3)")
public class LocalObjectStoreController {

    private final DocumentStorageService storage;
    private final UploadUrlSigner signer;
    private final DocumentService documentService;
    private final Clock clock;

    public LocalObjectStoreController(DocumentStorageService storage, UploadUrlSigner signer, DocumentService documentService,
                                      Clock clock) {
        this.storage = storage;
        this.signer = signer;
        this.documentService = documentService;
        this.clock = clock;
    }

    @PutMapping(DocumentStorageService.UPLOAD_CONTENT_PATH)
    @Operation(summary = "Upload the content of a document", description = "Authorised by the expires and signature query parameters of the pre-signed URL, not by a bearer token.")
    @SecurityRequirements // no bearer token: the request is authorised by its signature
    @ApiResponse(responseCode = "200", description = "Stored")
    @ApiResponse(responseCode = "404", description = "Unknown or already completed upload (UPLOAD_NOT_FOUND)")
    @ApiResponse(responseCode = "422", description = "Signature invalid or expired, wrong Content-Type, or more bytes than announced (UPLOAD_REJECTED)")
    public ResponseEntity<Void> uploadDocumentContent(@Parameter(description = "Upload id") @PathVariable UUID uploadId,
                                    @Parameter(description = "Expiry of the URL, epoch seconds") @RequestParam long expires,
                                    @Parameter(description = "HMAC signature of the URL") @RequestParam String signature,
                                    @RequestHeader(value = "Content-Type", required = false) String contentType,
                                    HttpServletRequest request) throws IOException {
        Document pending = documentService.getPendingUpload(uploadId);
        if (!signer.verify(uploadId, expires, pending.contentType(), pending.sizeBytes(), signature, clock.instant())) {
            throw new DomainException(ErrorCode.UPLOAD_REJECTED, "Upload URL is invalid or has expired");
        }
        if (contentType == null || !contentType.startsWith(pending.contentType())) {
            throw new DomainException(ErrorCode.UPLOAD_REJECTED, "Content-Type must be " + pending.contentType());
        }
        long received = storage.storeUploadedContent(uploadId, request.getInputStream(), pending.sizeBytes());
        documentService.completeUpload(uploadId, received);
        return ResponseEntity.ok().build();
    }
}
