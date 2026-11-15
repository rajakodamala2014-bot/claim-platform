package com.claims.platform.claims.core.service;

import com.claims.platform.claims.core.entity.Document;

import java.io.InputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.UUID;

/** Where document bytes live. Clients upload directly to the URL this returns (S3-style pre-signed PUT). */
public interface DocumentStorageService {
    /** Path of the upload URL when this service itself receives the bytes (local storage). */
    String UPLOAD_CONTENT_PATH = "/claimant/v1/uploads/{uploadId}/content";

    UploadTarget prepareUpload(Document document, Instant now);

    /**
     * Stores the bytes PUT to a pre-signed URL, refusing anything larger than {@code maxBytes}. Only used when
     * the upload URL points back at this service; with S3 the client sends the bytes to the bucket instead.
     *
     * @return number of bytes stored
     */
    long storeUploadedContent(UUID uploadId, InputStream content, long maxBytes);

    /**
     * @param url     where the app PUTs the bytes
     * @param headers headers the app must send with the PUT (e.g. Content-Type)
     */
    record UploadTarget(String url, String method, Map<String, String> headers, Instant expiresAt) {
    }
}
