package com.claims.platform.claims.core.infrastructure.storage;

import com.claims.platform.claims.core.application.DocumentStorage;
import com.claims.platform.claims.core.domain.Document;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;

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
 * Filesystem adapter for {@link DocumentStorage}: behaves like an S3 pre-signed PUT, served by
 * {@link LocalObjectStoreController}. Swap for an S3 adapter in real environments; the port stays the same.
 */
public class LocalDocumentStorage implements DocumentStorage {
    private final DocumentStorageProperties properties;
    private final UploadUrlSigner signer;

    public LocalDocumentStorage(DocumentStorageProperties properties, UploadUrlSigner signer) {
        this.properties = properties;
        this.signer = signer;
    }

    @Override
    public UploadTarget prepareUpload(Document document, Instant now) {
        Instant expiresAt = now.plus(properties.urlTtl());
        long expires = expiresAt.getEpochSecond();
        String signature = signer.sign(document.id(), expires, document.contentType(), document.sizeBytes());
        String url = properties.publicBaseUrl()
                + LocalObjectStoreController.PATH.replace("{uploadId}", document.id().toString())
                + "?expires=" + expires + "&signature=" + signature;
        return new UploadTarget(url, "PUT", Map.of("Content-Type", document.contentType()), expiresAt);
    }

    /**
     * Streams the body to {@code <storageDir>/<uploadId>}, refusing anything larger than {@code maxBytes}.
     * Written to a temp file first and moved into place, so a half-written upload is never visible.
     *
     * @return number of bytes stored
     */
    long write(UUID uploadId, InputStream body, long maxBytes) {
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
