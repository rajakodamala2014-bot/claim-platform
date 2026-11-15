package com.claims.platform.claims.handling.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

@Schema(description = "Questions or documents to ask the customer for")
public record CreateInformationRequest(@NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 300) String> items, Instant dueAt) {
}
