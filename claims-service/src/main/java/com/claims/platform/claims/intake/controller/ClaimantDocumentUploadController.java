package com.claims.platform.claims.intake.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.claims.platform.claims.intake.dto.request.DocumentUploadRequest;
import com.claims.platform.claims.intake.dto.response.DocumentUploadResponse;
import com.claims.platform.claims.intake.service.DocumentUploadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** C2: returns an uploadId and a pre-signed URL; the app PUTs the file straight to storage. */
@RestController
@Tag(name = "Claimant documents", description = "Uploading photos and documents for a claim")
public class ClaimantDocumentUploadController {
    private final DocumentUploadService documentUploadService;

    public ClaimantDocumentUploadController(DocumentUploadService documentUploadService) {
        this.documentUploadService = documentUploadService;
    }

    @PostMapping("/claimant/v1/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request a pre-signed upload URL", description = "C2. Registers the upload; the client then PUTs the bytes to the returned URL and passes uploadId when submitting a claim or answering an information request.")
    @ApiResponse(responseCode = "201", description = "Where and how to upload the file")
    @ApiResponse(responseCode = "400", description = "Invalid request body (VALIDATION_FAILED, with field errors)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "The caller is not a customer")
    public DocumentUploadResponse requestDocumentUpload(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DocumentUploadRequest body) {
        DocumentUploadService.RequestedUpload result = documentUploadService.requestUploadUrl(jwt.getSubject(), body.fileName(), body.contentType(),
                body.sizeBytes());
        return new DocumentUploadResponse(result.uploadId(), result.upload().url(), result.upload().method(),
                result.upload().headers(), result.upload().expiresAt());
    }
}
