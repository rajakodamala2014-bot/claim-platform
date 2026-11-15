package com.claims.platform.claims.core.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Metadata of an uploaded photo/document. Bytes go straight to storage through a pre-signed URL; the storage
 * key is the id (never the client's file name), so names can't be used for path tricks.
 */
public record Document(
        UUID id,
        String ownerId,
        ClaimId claimId,
        String fileName,
        String contentType,
        long sizeBytes,
        Status status,
        Instant createdAt,
        Instant uploadedAt
) {
    public enum Status { PENDING_UPLOAD, UPLOADED }

    public static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/heic", "application/pdf");
    public static final long MAX_SIZE_BYTES = 15L * 1024 * 1024;

    public Document {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(status, "status");
    }

    /** C2: the claimant announces an upload; validated before a URL is handed out. */
    public static Document requested(UUID id, String ownerId, String fileName, String contentType, long sizeBytes, Instant now) {
        List<ValidationException.FieldViolation> violations = new ArrayList<>();
        String name = fileName == null ? "" : fileName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.isEmpty() || name.length() > 255) {
            violations.add(new ValidationException.FieldViolation("fileName", "must be 1-255 characters"));
        }
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            violations.add(new ValidationException.FieldViolation("contentType", "must be one of " + ALLOWED_CONTENT_TYPES));
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            violations.add(new ValidationException.FieldViolation("sizeBytes", "must be between 1 and " + MAX_SIZE_BYTES));
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        return new Document(id, ownerId, null, name, contentType, sizeBytes, Status.PENDING_UPLOAD, now, null);
    }

    /** Storage received the bytes. Size may not exceed what was announced (and signed into the URL). */
    public Document uploaded(long actualSize, Instant at) {
        if (status != Status.PENDING_UPLOAD) {
            throw new DomainException(ErrorCode.UPLOAD_REJECTED, "Upload " + id + " was already completed");
        }
        if (actualSize <= 0 || actualSize > sizeBytes) {
            throw new DomainException(ErrorCode.UPLOAD_REJECTED, "Uploaded size " + actualSize + " does not match " + sizeBytes);
        }
        return new Document(id, ownerId, claimId, fileName, contentType, actualSize, Status.UPLOADED, createdAt, at);
    }

    /** Link to a claim (at FNOL or when answering an information request). */
    public Document attachTo(ClaimId claim, String actorId) {
        if (!ownerId.equals(actorId)) {
            throw new DomainException(ErrorCode.UPLOAD_NOT_FOUND, "Upload " + id + " not found");
        }
        if (status != Status.UPLOADED) {
            throw new DomainException(ErrorCode.UPLOAD_REJECTED, "Upload " + id + " has not been completed");
        }
        if (claimId != null && !claimId.equals(claim)) {
            throw new DomainException(ErrorCode.UPLOAD_REJECTED, "Upload " + id + " belongs to another claim");
        }
        return new Document(id, ownerId, claim, fileName, contentType, sizeBytes, status, createdAt, uploadedAt);
    }
}
