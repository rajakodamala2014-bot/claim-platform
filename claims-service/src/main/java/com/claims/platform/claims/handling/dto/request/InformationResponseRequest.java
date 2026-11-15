package com.claims.platform.claims.handling.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "The answer of the customer to an information request: text and / or uploaded documents")
public record InformationResponseRequest(@Size(max = 4000) String answer, @Size(max = 20) List<UUID> uploadIds) {
}
