package com.claims.platform.claims.intake.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The other party of a motor incident")
public record ThirdPartyRequest(String name, String vehicleRegistration, String insurer, String phone) {
}
