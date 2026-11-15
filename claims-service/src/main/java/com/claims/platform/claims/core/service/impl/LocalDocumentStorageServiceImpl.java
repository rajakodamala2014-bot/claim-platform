package com.claims.platform.claims.core.service.impl;

import com.claims.platform.claims.core.config.DocumentStorageProperties;
import com.claims.platform.claims.core.common.UploadUrlSigner;
import com.claims.platform.claims.core.service.DocumentStorageService;
import com.claims.platform.claims.core.entity.Document;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Filesystem implementation of {@link DocumentStorageService}: behaves like an S3 pre-signed PUT, served by
 * {@code LocalObjectStoreController}. Swap for an S3 implementation in real environments; the interface stays the same.
 */
public class LocalDocumentStorageServiceImpl implements DocumentStorageService {
    private final DocumentStorageProperties properties;
    private final UploadUrlSigner signer;

    public LocalDocumentStorageServiceImpl(DocumentStorageProperties properties, UploadUrlSigner signer) {
        this.properties = properties;
        this.signer = signer;
    }

    @Override
    public UploadTarget prepareUpload(Document document, Instant now) {
        Instant expiresAt = now.plus(properties.urlTtl());
        long expires = expiresAt.getEpochSecond();
        String signature = signer.sign(document.id(), expires, document.contentType(), document.sizeBytes());
        String url = properties.publicBaseUrl()
                + UPLOAD_CONTENT_PATH.replace("{uploadId}", document.id().toString())
                + "?expires=" + expires + "&signature=" + signature;
        return new UploadTarget(url, "PUT", Map.of("Content-Type", document.contentType()), expiresAt);
    }

    /** Written to a temp file first and moved into place, so a half-written upload is never visible. */
    @Override
    public long storeUploadedContent(UUID uploadId, InputStream body, long maxBytes) {
        Path dir = properties.storageDir();
        Path temp = null;
        try {
            Files.createDirectories(dir);
            temp = Files.createTempFile(dir, uploadId + "-", ".part");
            long total = 0;
            try (OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = body.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        throw new DomainException(ErrorCode.UPLOAD_REJECTED, "Upload is larger than the announced size");
                    }
                    out.write(buffer, 0, read);
                }
            }
            Files.move(temp, dir.resolve(uploadId.toString()), StandardCopyOption.REPLACE_EXISTING);
            temp = null;
            return total;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store upload " + uploadId, ex);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort clean-up of a rejected upload
                }
            }
        }
    }
}
