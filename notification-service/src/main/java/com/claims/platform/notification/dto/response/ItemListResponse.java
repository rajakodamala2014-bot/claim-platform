package com.claims.platform.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A list of items")
public record ItemListResponse<T>(List<T> items) {
}
