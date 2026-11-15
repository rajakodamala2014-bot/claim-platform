package com.claims.platform.claims.handling.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "The claim status after an information request was answered")
public record InformationResponseResult(UUID claimId, UUID requestId, String status) {
}
