package com.claims.platform.claims.intake.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(description = "How to reach the customer about this claim")
public record ContactRequest(@Size(max = 40) String phone, @Email @Size(max = 200) String email) {
}
