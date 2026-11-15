package com.claims.platform.claims.intake.api;

import com.claims.platform.claims.intake.api.ClaimantRequests.UploadRequest;
import com.claims.platform.claims.intake.application.RequestUpload;
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
public class ClaimantUploadController {
    private final RequestUpload requestUpload;

    public ClaimantUploadController(RequestUpload requestUpload) {
        this.requestUpload = requestUpload;
    }

    public record UploadResponse(UUID uploadId, String uploadUrl, String method, Map<String, String> headers,
                                 Instant expiresAt) {
    }

    @PostMapping("/claimant/v1/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadRequest body) {
        RequestUpload.Result result = requestUpload.request(jwt.getSubject(), body.fileName(), body.contentType(),
                body.sizeBytes());
        return new UploadResponse(result.uploadId(), result.upload().url(), result.upload().method(),
                result.upload().headers(), result.upload().expiresAt());
    }
}
