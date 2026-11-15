package com.claims.platform.claims.core.application;

import com.claims.platform.claims.core.domain.Document;

import java.time.Instant;
import java.util.Map;

/** Where document bytes live. Clients upload directly to the URL this returns (S3-style pre-signed PUT). */
public interface DocumentStorage {

    UploadTarget prepareUpload(Document document, Instant now);

    /**
     * @param url     where the app PUTs the bytes
     * @param headers headers the app must send with the PUT (e.g. Content-Type)
     */
    record UploadTarget(String url, String method, Map<String, String> headers, Instant expiresAt) {
    }
}
