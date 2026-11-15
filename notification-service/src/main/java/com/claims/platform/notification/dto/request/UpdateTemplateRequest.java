package com.claims.platform.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "New wording of a template; {{placeholders}} are kept as typed")
public record UpdateTemplateRequest(
        @Schema(example = "We have received your claim {{claimNumber}}") @NotBlank @Size(max = 200) String subject,
        @Schema(example = "Thank you. Your claim {{claimNumber}} is with our team.") @NotBlank @Size(max = 4000) String body) {
}
