package com.claims.platform.claims.handling.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.dto.response.ClaimantInformationRequestResponse;
import java.util.List;

@Schema(description = "The information requests on a claim")
public record InformationRequestListResponse(List<ClaimantInformationRequestResponse> items) {
}
