package com.claims.platform.claims.intake.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "File the customer is about to upload")
public record DocumentUploadRequest(@NotBlank @Size(max = 255) String fileName, @NotBlank String contentType,
                            @Positive long sizeBytes) {
}
