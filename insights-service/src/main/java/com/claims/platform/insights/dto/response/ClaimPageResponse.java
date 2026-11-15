package com.claims.platform.insights.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A page of claims")
public record ClaimPageResponse(
        List<ClaimItemResponse> items,
        @Schema(description = "Pass as cursor to get the next page; null on the last page") String nextCursor) {
}
